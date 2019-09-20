package uk.gov.homeoffice.cirium.services.feed

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Cancellable, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.AskableActorRef
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import uk.gov.homeoffice.cirium.services.entities.{ CiriumFlightStatus, CiriumFlightStatusResponse, CiriumInitialResponse, CiriumItemListResponse }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object Cirium {

  abstract case class Client(appId: String, appKey: String, entryPoint: String)(implicit system: ActorSystem) {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import uk.gov.homeoffice.cirium.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] = makeRequest(entryPoint).map(res => Unmarshal[HttpResponse](res)
      .to[CiriumInitialResponse]).flatten

    def backwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] =
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

    def start(goBackHops: Int = 0, step: Int = 1000): Future[Source[CiriumFlightStatus, Cancellable]] = {
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
            client.forwards(s, step).flatMap(r => {
              if (r.items.nonEmpty) {
                (askableLatestItemActor ? r.items.last).map(_ => r.items)
              } else Future(List())
            })
          })
          .mapConcat(identity)
          .mapAsync(20) { item =>
            client.requestItem(item)
          }
          .map((r: CiriumFlightStatusResponse) => r.flightStatuses)
          .collect {
            case Some(fs) =>
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
            println(s"Going Back $step from $si")
            client.backwards(si, step).map(r => r.items.head)
          }).flatten)
  }

}
