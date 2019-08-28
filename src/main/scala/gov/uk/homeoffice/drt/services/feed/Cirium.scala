package gov.uk.homeoffice.drt.services.feed

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Cancellable, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.AskableActorRef
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import gov.uk.homeoffice.drt.services.entities._
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object Cirium {

  abstract case class Client(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import gov.uk.homeoffice.drt.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] = makeRequest(entryPoint).map(res => Unmarshal[HttpResponse](res)
      .to[CiriumInitialResponse]).flatten

    def backwards(latestItemLocation: String, step: Int = 1000) =
      makeRequest(latestItemLocation + s"/previous/$step")
        .map(res => Unmarshal[HttpResponse](res)
          .to[CiriumItemListResponse]).flatten

    def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] = makeRequest(latestItemLocation + s"/next/$step")
      .map(res => Unmarshal[HttpResponse](res)
        .to[CiriumItemListResponse]).flatten

    def makeRequest(endpoint: String): Future[HttpResponse] = {

      val uri = Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")

      sendReceive(uri)
    }

    def sendReceive(uri: Uri): Future[HttpResponse]

    def requestItem(endpoint: String): Future[CiriumFlightStatusResponse] = makeRequest(endpoint).map(res => {
      Unmarshal[HttpResponse](res)
        .to[CiriumFlightStatusResponse]
    }).flatten
  }

  class ProdClient(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) extends Client(appId, appKey, entryPoint) {
    override def sendReceive(uri: Uri): Future[HttpResponse] = Http()
      .singleRequest(HttpRequest(HttpMethods.GET, uri))
  }

  case object Ask

  class CiriumLastItemActor extends Actor with ActorLogging {
    var lastItem: Option[String] = None

    def receive: Receive = {

      case item: String =>
        lastItem = Option(item)

        sender() ! "Ack"
      case Ask =>
        sender() ! lastItem
    }
  }

  case class Feed(client: Client)(implicit system: ActorSystem) {
    implicit val timeout = new Timeout(30 seconds)

    val askableLatestItemActor: AskableActorRef = system.actorOf(Props(classOf[CiriumLastItemActor]), "latest-item-actor")

    def start(goBackHops: Int = 0, step: Int = 1000) = {
      val startingPoint = client
        .initialRequest()
        .map(crp => goBack(crp.item, goBackHops, step))
        .flatten

      tick(startingPoint, step)
    }

    def tick(start: Future[String], step: Int): Future[Source[CiriumFlightStatus, Cancellable]] = {

      start.map(s => askableLatestItemActor ? s).map { _ =>
        val tickingSource: Source[CiriumFlightStatus, Cancellable] = Source
          .tick(1 milli, 100 millis, NotUsed)
          .mapAsync(1)(_ => {
            (askableLatestItemActor ? Ask).map {
              case s: Some[String] => s
              case _ => None
            }

          })
          .collect {
            case Some(s) => s
          }
          .mapAsync(1)(s => {
            println(s"About to request another $step")
            client.forwards(s, step).flatMap(r => {
              println(s"starting another $step ${r.request.itemId}")
              (askableLatestItemActor ? r.items.head).map(_ => r.items)
            })
          })
          .mapConcat(identity)
          .mapAsync(20) { item =>
            println(s"Fetching item: $item")
            client.requestItem(item)
          }
          .map(_.flightStatuses)
          .collect {
            case Some(fs) =>
              println(s"Got ${fs.size} updates")
              fs
          }
          .mapConcat(identity)

        tickingSource
      }
    }

    def goBack(startItem: String, hops: Int = 4, step: Int = 1000): Future[String] = (0 until hops)
      .foldLeft(
        Future(startItem))(
          (prev: Future[String], _) => prev.map(si => {

            println(s"Current start item is $si")
            client.backwards(si, step).map(r => r.items.head)
          }).flatten)
  }

}

