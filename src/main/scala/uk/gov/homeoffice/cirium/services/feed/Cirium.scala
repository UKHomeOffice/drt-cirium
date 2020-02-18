package uk.gov.homeoffice.cirium.services.feed

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Cancellable, Props, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.AskableActorRef
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import uk.gov.homeoffice.cirium.services.entities.{ CiriumFlightStatusResponse, CiriumFlightStatusResponseFailure, CiriumFlightStatusResponseSuccess, CiriumInitialResponse, CiriumItemListResponse, CiriumTrackableStatus }

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait CiriumClientLike {
  def initialRequest(): Future[CiriumInitialResponse]

  def backwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse]

  def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse]

  def makeRequest(endpoint: String): Future[HttpResponse]

  def sendReceive(uri: Uri): Future[HttpResponse]

  def requestItem(endpoint: String): Future[CiriumFlightStatusResponse]

}

object Cirium {
  val log = Logger(getClass)

  abstract case class Client(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) extends CiriumClientLike {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import uk.gov.homeoffice.cirium.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] = makeRequest(entryPoint).map(res => Unmarshal[HttpResponse](res)
      .to[CiriumInitialResponse]).flatten

    def backwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] =
      makeRequest(latestItemLocation + s"/previous/$step")
        .map(res => Unmarshal[HttpResponse](res)
          .to[CiriumItemListResponse]).flatten

    def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] = makeRequest(latestItemLocation + s"/next/$step")
      .map(res => {
        val futureStatusResponse: Future[CiriumItemListResponse] = Unmarshal[HttpResponse](res)
          .to[CiriumItemListResponse]
        futureStatusResponse.map { statusResponse =>
          log.info(s"Requested next $step from $latestItemLocation and got ${statusResponse.items.size}")
        }
        futureStatusResponse
      }).flatten

    def makeRequest(endpoint: String): Future[HttpResponse] = {
      implicit val s: Scheduler = system.scheduler
      Retry.retry(
        sendReceive(Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")),
        Retry.fibonacciDelay,
        10,
        5 seconds)

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
    implicit val timeout = new Timeout(30 seconds)

    val askableLatestItemActor: AskableActorRef = system.actorOf(Props(classOf[CiriumLastItemActor]), "latest-item-actor")

    def start(goBackHops: Int = 0, step: Int = 1000): Future[Source[CiriumTrackableStatus, Cancellable]] = {
      val startingPoint = client
        .initialRequest()
        .map(crp => goBack(crp.item, goBackHops, step))
        .flatten

      tick(startingPoint, step)
    }

    def tick(start: Future[String], step: Int): Future[Source[CiriumTrackableStatus, Cancellable]] = {

      start.map(s => askableLatestItemActor ? LatestItem(s)).map { _ =>
        val tickingSource: Source[CiriumTrackableStatus, Cancellable] = Source
          .tick(1 milli, pollEveryMillis millis, NotUsed)
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

    def goBack(startItem: String, hops: Int = 4, step: Int = 1000): Future[String] = (0 until hops)
      .foldLeft(Future(startItem))(
        (prev: Future[String], _) => prev.map(si => {
          log.info(s"Going Back $step from $si")
          client.backwards(si, step).map(r => r.items.head)
        }).flatten)
  }

}
