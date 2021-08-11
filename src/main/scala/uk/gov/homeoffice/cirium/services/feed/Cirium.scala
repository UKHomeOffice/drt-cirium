package uk.gov.homeoffice.cirium.services.feed

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.services.entities._

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.matching.Regex
import scala.util.{ Failure, Success }

trait CiriumClientLike {
  def initialRequest(): Future[CiriumInitialResponse]

  def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse]

  def forwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse]

  def makeRequest(endpoint: String): Future[HttpResponse]

  def sendReceive(uri: Uri): Future[HttpResponse]

  def requestItem(endpoint: String): Future[CiriumFlightStatusResponse]

}

object Cirium {
  private val log = LoggerFactory.getLogger(getClass)

  abstract case class Client(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) extends CiriumClientLike {

    implicit val materializer: Materializer = Materializer.createMaterializer(system)

    import uk.gov.homeoffice.cirium.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] =
      makeRequest(entryPoint).flatMap(res => Unmarshal[HttpResponse](res).to[CiriumInitialResponse])

    def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] =
      requestAndUnmarshal(latestItemLocation + s"/previous/$step")

    def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] =
      requestAndUnmarshal(latestItemLocation + s"/next/$step")

    private def requestAndUnmarshal(uri: String): Future[CiriumItemListResponse] =
      makeRequest(uri)
        .flatMap(res => {
          val eventualResponse = Unmarshal[HttpResponse](res).to[CiriumItemListResponse]
          eventualResponse.onComplete {
            case Success(v) => v
            case Failure(exception) =>
              log.error(s"1Error parsing Cirium response from $uri. Response was ${Await.result(res.entity.dataBytes.runFold("") { case (a, b) => a + b.utf8String }, 1.second)}", exception)
              Future.failed(exception)
          }
          eventualResponse
        })
        .recover {
          case error: Throwable =>
            log.error(s"2Error parsing Cirium response from $uri", error)
            CiriumItemListResponse(CiriumRequestMetaData("", None, None, ""), List())
        }

    def makeRequest(endpoint: String): Future[HttpResponse] = {
      implicit val s: Scheduler = system.scheduler
      Retry.retry(
        sendReceive(Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")),
        Retry.fibonacciDelay,
        10,
        5.seconds)
    }

    def sendReceive(uri: Uri): Future[HttpResponse]

    def requestItem(endpoint: String): Future[CiriumFlightStatusResponse] = makeRequest(endpoint).flatMap(res => {
      Unmarshal[HttpResponse](res)
        .to[CiriumFlightStatusResponseSuccess]
        .recover {
          case error: Throwable =>
            log.error(s"Error parsing Cirium response from $endpoint", error)
            CiriumFlightStatusResponseFailure(error)
        }
    })
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

  case class Feed(client: CiriumClientLike, pollEveryMillis: Int, backwardsStrategy: BackwardsStrategy)(implicit system: ActorSystem) {
    implicit val timeout: Timeout = new Timeout(5.seconds)

    val latestItemActor: ActorRef = system.actorOf(Props(classOf[CiriumLastItemActor]), "latest-item-actor")

    def start(step: Int): Future[Source[CiriumTrackableStatus, Cancellable]] = {
      val startingPoint = client
        .initialRequest()
        .flatMap(crp => backwardsStrategy.backUntil(crp.item))

      tick(startingPoint, step)
    }

    def tick(start: Future[String], step: Int): Future[Source[CiriumTrackableStatus, Cancellable]] =
      start
        .map(s => latestItemActor ? LatestItem(s))
        .map { _ =>
          val tickingSource: Source[CiriumTrackableStatus, Cancellable] = Source
            .tick(1.milliseconds, pollEveryMillis.milliseconds, NotUsed)
            .mapAsync(1)(_ => {
              (latestItemActor ? Ask).map {
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
                  (latestItemActor ? LatestItem(r.items.last)).map(_ => r.items)
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
}

trait BackwardsStrategy {
  def backUntil(startItem: String): Future[String]
}

case class BackwardsStrategyImpl(client: CiriumClientLike, targetTime: DateTime) extends BackwardsStrategy {
  private val log = LoggerFactory.getLogger(getClass)
  private val dateFromUrlRegex: Regex = ".+/json/([0-9]{4})/([0-9]{2})/([0-9]{2})/([0-9]{2})/([0-9]{2})/[0-9]{2}/[0-9]{3,4}/.+".r

  def backUntil(startItem: String): Future[String] = {
    client.backwards(startItem, 1000).flatMap { c =>
      val firstItem = c.items.head
      println(s"in backwards: ${c.items.head}")
      firstItem match {
        case dateFromUrlRegex(y, m, d, h, min) =>
          val dateTime = new DateTime(y.toInt, m.toInt, d.toInt, h.toInt, min.toInt)
          if (dateTime.getMillis <= targetTime.getMillis) {
            println(s"date ${dateTime.toDateTimeISO} is far enough")
            Future.successful(firstItem)
          } else {
            println(s"date ${dateTime.toDateTimeISO} is not far enough (>= ${targetTime.toDateTimeISO})")
            backUntil(firstItem)
          }
        case _ =>
          log.error(s"Failed to extract the date from $firstItem")
          Future.failed(new Exception(s"Failed to extract the date from $firstItem"))
      }
    }
  }
}
