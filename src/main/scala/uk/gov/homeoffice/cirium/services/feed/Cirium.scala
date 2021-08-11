package uk.gov.homeoffice.cirium.services.feed

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Cancellable, Props, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.AskableActorRef
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.services.entities._

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.matching.Regex

trait CiriumClientLike {
  def initialRequest(): Future[CiriumInitialResponse]

  def backwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse]

  def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse]

  def makeRequest(endpoint: String): Future[HttpResponse]

  def sendReceive(uri: Uri): Future[HttpResponse]

  def requestItem(endpoint: String): Future[CiriumFlightStatusResponse]

}

object Cirium {
  private val log = LoggerFactory.getLogger(getClass)

  abstract case class Client(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) extends CiriumClientLike {

    implicit val materializer: Materializer = Materializer.createMaterializer(system)

    import uk.gov.homeoffice.cirium.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] = makeRequest(entryPoint).map(res => Unmarshal[HttpResponse](res)
      .to[CiriumInitialResponse]).flatten

    def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] =
      makeRequest(latestItemLocation + s"/previous/$step")
        .flatMap { res =>
          Unmarshal[HttpResponse](res).to[CiriumItemListResponse]
        }

    def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] =
      makeRequest(latestItemLocation + s"/next/$step")
        .map { res =>
          val futureStatusResponse = Unmarshal[HttpResponse](res).to[CiriumItemListResponse]
          futureStatusResponse.foreach { statusResponse =>
            log.info(s"Requested next $step from $latestItemLocation and got ${statusResponse.items.size}")
          }
          futureStatusResponse
        }
        .flatten

    def makeRequest(endpoint: String): Future[HttpResponse] = {
      implicit val s: Scheduler = system.scheduler
      Retry.retry(
        sendReceive(Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")),
        Retry.fibonacciDelay,
        10,
        5.seconds)
    }

    def sendReceive(uri: Uri): Future[HttpResponse]

    def requestItem(endpoint: String): Future[CiriumFlightStatusResponse] = makeRequest(endpoint).map(res => {
      Unmarshal[HttpResponse](res)
        .to[CiriumFlightStatusResponseSuccess]
        .recover {
          case error: Throwable =>
            log.error(s"Error parsing Cirium response from $endpoint", error)
            CiriumFlightStatusResponseFailure(error)
        }
    }).flatten
  }

  class ProdClient(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) extends Client(appId, appKey, entryPoint) {
    override def sendReceive(uri: Uri): Future[HttpResponse] = Http()
      .singleRequest(HttpRequest(HttpMethods.GET, uri))
  }

  case object Ask

  case class LatestItem(endpoint: Option[String])

  case object LatestItem {
    def apply(endpoint: String): LatestItem = LatestItem(Option(endpoint))
  }

  class CiriumLastItemActor extends Actor with ActorLogging {
    var lastItem: LatestItem = LatestItem(None)

    def receive: Receive = {

      case latest: LatestItem =>
        log.info(s"Latest item is ${latest.endpoint.getOrElse("not set")}")

        lastItem = latest

        sender() ! "Ack"
      case Ask =>
        sender() ! lastItem
    }
  }

  case class Feed(client: CiriumClientLike, pollEveryMillis: Int)(implicit system: ActorSystem) {
    implicit val timeout: Timeout = new Timeout(5.seconds)

    val askableLatestItemActor: AskableActorRef = system.actorOf(Props(classOf[CiriumLastItemActor]), "latest-item-actor")

    def start(goBackHops: Int = 0, step: Int): Future[Source[CiriumTrackableStatus, Cancellable]] = {
      val startingPoint = client
        .initialRequest()
        .flatMap(crp => goBack(crp.item, goBackHops, step))

      tick(startingPoint, step)
    }

    def tick(start: Future[String], step: Int): Future[Source[CiriumTrackableStatus, Cancellable]] = {
      start
        .map(s => askableLatestItemActor ? LatestItem(s))
        .map { _ =>
          val tickingSource: Source[CiriumTrackableStatus, Cancellable] = Source
            .tick(1.milliseconds, pollEveryMillis.milliseconds, NotUsed)
            .mapAsync(1)(_ => {
              (askableLatestItemActor ? Ask).map {
                case LatestItem(endpoint) => endpoint
                case _ => None
              }
            })
            .collect {
              case Some(s) => s
            }
            .mapAsync(1)(s => {
              client.forwards(s, step).flatMap(r => {
                if (r.items.nonEmpty) {
                  (askableLatestItemActor ? LatestItem(r.items.last)).map(_ => r.items)
                } else {
                  Future(List())
                }
              })
            })
            .mapConcat(identity)
            .mapAsync(20) { item =>
              client.requestItem(item)
            }
            .collect {
              case CiriumFlightStatusResponseSuccess(meta, maybeFS) if maybeFS.isDefined =>
                val trackableFlights: immutable.Seq[CiriumTrackableStatus] = maybeFS.get.map { f =>
                  CiriumTrackableStatus(f, meta.url, System.currentTimeMillis)
                }
                trackableFlights
            }
            .mapConcat(identity)

          tickingSource
        }
    }

    //    val regex = "2021/08/10/06/53/29/860/AJ4cq7".r
    val regex: Regex = ".+/json/([0-9]{4})/([0-9]{2})/([0-9]{2})/([0-9]{2})/([0-9]{2})/[0-9]{2}/[0-9]{3,4}/.+".r

    def goBack(startItem: String, hops: Int, step: Int): Future[String] = (0 until hops)
      .foldLeft(Future(startItem))(
        (prev: Future[String], _) =>
          prev.flatMap { si =>
            log.info(s"Going Back $step from $si")
            client.backwards(si, step).map { r =>
              val next = r.items.head
              val date = next match {
                case regex(y, m, d, h, min) =>
                  val dateTime = new DateTime(y.toInt, m.toInt, d.toInt, h.toInt, min.toInt)
                  val now = new DateTime()
                  //                  val timeDiff = 2 * 24 * 60 * 60000
                  val timeDiff = 7 * 60 * 60000
                  val ago = now.getMillis - dateTime.getMillis
                  if (ago > timeDiff) {
                    println(s"date ${dateTime.toDateTimeISO} is far enough")
                  } else
                    println(s"date ${dateTime.toDateTimeISO} is not far enough (${ago / 60000} minutes ago)")

                  val dateStr = Option(s"$y-$m-$d")
                case _ => None
              }
              println(s"backwards yielded $next")
              next
            }
              .recover {
                case t =>
                  println(s"failed to go backwards: ${t.toString}")
                  throw new Exception(s"Damn")
              }
          })
  }

}
