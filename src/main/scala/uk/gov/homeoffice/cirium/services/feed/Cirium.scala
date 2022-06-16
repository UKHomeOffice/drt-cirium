package uk.gov.homeoffice.cirium.services.feed

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.MetricsCollector
import uk.gov.homeoffice.cirium.services.entities._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success}

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

  abstract case class Client(appId: String, appKey: String, entryPoint: String, metricsCollector: MetricsCollector)(implicit system: ActorSystem, executionContext: ExecutionContext) extends CiriumClientLike {

    implicit val materializer: Materializer = Materializer.createMaterializer(system)

    import uk.gov.homeoffice.cirium.JsonSupport._

    def initialRequest(): Future[CiriumInitialResponse] = {
      makeRequest(entryPoint).flatMap { res =>
        Unmarshal[HttpResponse](res).to[CiriumInitialResponse].recoverWith {
          case e =>
            log.error(s"Error while parsing initialRequest", e)
            Future.failed(new Exception(s"Error while making InitialRequest", e))
        }
      }
    }

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
              log.error(s"Error parsing Cirium response from $uri: ${exception.getMessage}")
              Future.failed(exception)
          }
          eventualResponse
        })
        .recover {
          case error: Throwable =>
            log.error(s"Failed to get a response from cirium end point: ${error.getMessage}")
            metricsCollector.errorCounterMetric("requestAndUnmarshal-CiriumItemListResponse")
            CiriumItemListResponse.empty
        }

    def makeRequest(endpoint: String): Future[HttpResponse] =
      Retry
        .retry(
          sendReceive(Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")),
          Retry.fibonacciDelay, 10, 5.seconds
        )
        .flatMap { response =>
          response.status match {
            case StatusCodes.OK => Future.successful(response)
            case status => log.warn(s"Status of http response is not 200 Ok $status")
              Future.failed(new Exception(s"$status status while cirium request"))
          }
        }

    def sendReceive(uri: Uri): Future[HttpResponse]

    def requestItem(endpoint: String): Future[CiriumFlightStatusResponse] = makeRequest(endpoint)
      .flatMap(res => {
        res.status match {
          case StatusCodes.OK =>
            Unmarshal[HttpResponse](res)
              .to[CiriumFlightStatusResponseSuccess].recover {
              case error: Throwable =>
                log.error(s"Error parsing CiriumFlightStatusResponseSuccess from $endpoint: ${error.getMessage}")
                metricsCollector.errorCounterMetric("requestItem-CiriumFlightStatusResponse")
                CiriumFlightStatusResponseFailure(error)
            }
          case _ => metricsCollector.errorCounterMetric("requestItem-ciriumResponseStatus")
            Future.failed(new Exception(s"Unable to get valid response $res"))
        }
      })
      .recover {
        case t =>
          log.error(s"Failed to request item $endpoint")
          CiriumFlightStatusResponseFailure(t)
      }
  }

  class ProdClient(appId: String, appKey: String, entryPoint: String, metricsCollector: MetricsCollector)(implicit system: ActorSystem, executionContext: ExecutionContext) extends Client(appId, appKey, entryPoint, metricsCollector) {
    override def sendReceive(uri: Uri): Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.GET, uri))
  }

  case object Ask

  case class LatestItem(endpoint: Option[String])

  case object LatestItem {
    def apply(endpoint: String): LatestItem = LatestItem(Option(endpoint))
  }

  case class Feed(client: CiriumClientLike, pollInterval: FiniteDuration, backwardsStrategy: BackwardsStrategy)(implicit system: ActorSystem, executionContext: ExecutionContext) {
    implicit val timeout: Timeout = new Timeout(5.seconds)

    def start(step: Int): Future[Source[CiriumTrackableStatus, NotUsed]] =
      client.initialRequest()
        .flatMap(cir => backwardsStrategy.backwardsFrom(cir.item))
        .map { startUrl =>
          Source
            .unfoldAsync((startUrl, List[String]())) { case (url, lastStatusUrls) =>
              client.forwards(url, step).map {
                case CiriumItemListResponse(items) if items.isEmpty =>
                  log.info(s"No records to fetch from $url")
                  Option((url, lastStatusUrls), (url, lastStatusUrls))
                case CiriumItemListResponse(newStatusUrls) =>
                  log.info(s"${newStatusUrls.size} records to fetch from $url")
                  Option((newStatusUrls.last, newStatusUrls), (url, lastStatusUrls))
              }
            }
            .throttle(1, pollInterval)
            .mapConcat { case (_, statusUrls) => statusUrls }
            .mapAsync(10)(client.requestItem)
            .collect {
              case CiriumFlightStatusResponseSuccess(meta, Some(statuses)) =>
                statuses.map(status =>
                  CiriumTrackableStatus(amendCiriumFlightStatus(status), meta.url, System.currentTimeMillis))
            }
            .mapConcat(identity)
        }
  }

  def amendCiriumFlightStatus(status: CiriumFlightStatus): CiriumFlightStatus = {
    val isSingleTerminalPort = Set("ABZ", "CWL", "HUY", "INV", "LBA", "SEN", "SOU", "BOH", "MME", "NQY", "NWI")
      .contains(status.arrivalAirportFsCode.toUpperCase)
    val emptyTerminal = status.airportResources.exists(_.arrivalTerminal.isEmpty)

    if (isSingleTerminalPort && emptyTerminal)
      status.copy(airportResources = status.airportResources.map(ar => ar.copy(arrivalTerminal = Option("T1"))))
    else status
  }
}

trait BackwardsStrategy {
  def backwardsFrom(startItem: String): Future[String]
}

case class BackwardsStrategyImpl(client: CiriumClientLike, targetTime: DateTime, metricsCollector: MetricsCollector)(implicit executionContext: ExecutionContext) extends BackwardsStrategy {
  private val log = LoggerFactory.getLogger(getClass)
  private val dateFromUrlRegex: Regex = ".+/json/([0-9]{4})/([0-9]{2})/([0-9]{2})/([0-9]{2})/([0-9]{2})/[0-9]{2}/[0-9]{3,4}/.+".r

  def backwardsFrom(startItem: String): Future[String] = {
    client.backwards(startItem, 1000).flatMap { c =>
      val firstItem = c.items.head
      firstItem match {
        case dateFromUrlRegex(y, m, d, h, min) =>
          val dateTime = new DateTime(y.toInt, m.toInt, d.toInt, h.toInt, min.toInt)
          if (dateTime.getMillis <= targetTime.getMillis) {
            log.info(s"Reached back to ${dateTime.toDateTimeISO}. Will start processing forwards now")
            Future.successful(firstItem)
          } else {
            log.info(s"Reached back to ${dateTime.toDateTimeISO}. Aiming for ${targetTime.toDateTimeISO}")
            backwardsFrom(firstItem)
          }
        case _ =>
          log.error(s"Failed to extract the date from $firstItem")
          metricsCollector.errorCounterMetric("backUntil-dateFromFirstItem")
          Future.failed(new Exception(s"Failed to extract the date from $firstItem"))
      }
    }
  }
}
