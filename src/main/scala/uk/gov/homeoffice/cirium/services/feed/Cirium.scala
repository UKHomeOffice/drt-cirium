package uk.gov.homeoffice.cirium.services.feed

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.MetricsCollector
import uk.gov.homeoffice.cirium.services.entities.CiriumStatusSchedule.ciriumFreightFlightTypes
import uk.gov.homeoffice.cirium.services.entities._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait CiriumClientLike {
  def initialRequest(): Future[CiriumInitialResponse]

  def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse]

  def forwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse]

  def makeRequest(endpoint: String, maybeMaxRetries: Option[Int]): Future[HttpResponse]

  def sendReceive(request: HttpRequest): Future[HttpResponse]

  def fetchFlightStatus(endpoint: String): Future[CiriumFlightStatusResponse]

}

object Cirium {
  private val log = LoggerFactory.getLogger(getClass)

  abstract class Client(metricsCollector: MetricsCollector)(
      implicit
      system: ActorSystem,
      executionContext: ExecutionContext
  ) extends CiriumClientLike {
    protected def latestFeedEndpoint: String
    protected def previousFeedEndpoint(item: String, batchSize: Int): String
    protected def nextFeedEndpoint(item: String, batchSize: Int): String
    protected def feedItemEndpoint(item: String): String
    protected def request(endpoint: String): HttpRequest
    protected def requestFailureMetric: String

    implicit val materializer: Materializer = Materializer.createMaterializer(system)

    import uk.gov.homeoffice.cirium.JsonSupport._

    val initialRequestMaxRetries: Option[Int] = None
    val itemListMaxRetries: Option[Int] = None
    val flightStatusMaxRetries: Option[Int] = Option(15)

    override def initialRequest(): Future[CiriumInitialResponse] = {
      makeRequest(latestFeedEndpoint, initialRequestMaxRetries).flatMap { res =>
        Unmarshal[HttpResponse](res).to[CiriumInitialResponse].recoverWith {
          case e =>
            log.error(s"Error while parsing initialRequest", e)
            Future.failed(new Exception(s"Error while making InitialRequest", e))
        }
      }
    }

    override def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] =
      fetchItemList(previousFeedEndpoint(latestItemLocation, step))

    override def forwards(latestItemLocation: String, step: Int = 1000): Future[CiriumItemListResponse] =
      fetchItemList(nextFeedEndpoint(latestItemLocation, step))

    private def fetchItemList(uri: String): Future[CiriumItemListResponse] =
      makeRequest(uri, itemListMaxRetries)
        .flatMap(res => Unmarshal[HttpResponse](res).to[CiriumItemListResponse])
        .recover {
          case error: Throwable =>
            log.error(s"Failed to get a response from cirium end point: ${error.getMessage}")
            metricsCollector.errorCounterMetric("fetchItemList-CiriumItemListResponse")
            CiriumItemListResponse.empty
        }

    private def safeEndpoint(endpoint: String): String = Uri(endpoint).withQuery(Uri.Query.Empty).toString()

    private def recordRequestFailure(endpoint: String, status: StatusCode): Unit = {
      log.warn(s"Cirium request failed: endpoint=${safeEndpoint(endpoint)} status=$status")
      metricsCollector.errorCounterMetric(s"$requestFailureMetric-${status.intValue()}")
    }

    override def makeRequest(endpoint: String, maybeMaxRetries: Option[Int]): Future[HttpResponse] =
      Try(request(endpoint)).fold(
        Future.failed,
        request =>
          Retry.retry(
            sendReceive(request)
              .flatMap { response =>
                response.status match {
                  case StatusCodes.OK => Future.successful(response)
                  case status         =>
                    recordRequestFailure(endpoint, status)
                    response.discardEntityBytes()
                    Future.failed(new Exception(s"$status status while cirium request"))
                }
              },
            Retry.fibonacci(180).map(_.second),
            maybeMaxRetries,
            5.seconds
          )
      )

    def fetchFlightStatus(endpoint: String): Future[CiriumFlightStatusResponse] =
      Try(feedItemEndpoint(endpoint)).fold(Future.failed, fetchFlightStatusFrom(_, endpoint))

    private def fetchFlightStatusFrom(
        endpoint: String,
        endpointDescription: String
    ): Future[CiriumFlightStatusResponse] =
      makeRequest(endpoint, flightStatusMaxRetries)
        .flatMap { res =>
          res.status match {
            case StatusCodes.OK =>
              Unmarshal[HttpResponse](res)
                .to[CiriumFlightStatusResponseSuccess].recover {
                  case error: Throwable =>
                    log.error(
                      s"Error parsing CiriumFlightStatusResponseSuccess from $endpointDescription: ${error.getMessage}"
                    )
                    metricsCollector.errorCounterMetric("requestItem-CiriumFlightStatusResponse")
                    CiriumFlightStatusResponseFailure(error)
                }
            case _ =>
              metricsCollector.errorCounterMetric("requestItem-ciriumResponseStatus")
              Future.failed(new Exception(s"Unable to get valid response $res"))
          }
        }
        .recover {
          case t =>
            log.error(s"Failed to request item $endpointDescription")
            CiriumFlightStatusResponseFailure(t)
        }
  }

  case class Feed(
      client: CiriumClientLike,
      pollInterval: FiniteDuration,
      backwardsStrategy: BackwardsStrategy,
      metricsCollector: MetricsCollector
  )(implicit system: ActorSystem, executionContext: ExecutionContext) {
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
            .mapAsync(10)(client.fetchFlightStatus)
            .collect {
              case CiriumFlightStatusResponseSuccess(meta, Some(statuses)) =>
                statuses
                  .flatMap { status =>
                    status.schedule match {
                      case Some(schedule) if !ciriumFreightFlightTypes.contains(schedule.flightType) =>
                        Some(CiriumTrackableStatus(amendCiriumFlightStatus(status), meta.url, System.currentTimeMillis))
                      case Some(_) =>
                        None
                      case None =>
                        metricsCollector.infoCounterMetric("droppedStatus-missingOrInvalidSchedule")
                        log.warn(
                          s"[Feed][start] Dropping flight status ${status.flightId} (${status.carrierFsCode}${status.flightNumber}) due to missing or invalid schedule"
                        )
                        None
                    }
                  }
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

case class BackwardsStrategyImpl(
    client: CiriumClientLike,
    targetTime: DateTime,
    metricsCollector: MetricsCollector
)(implicit executionContext: ExecutionContext) extends BackwardsStrategy {
  private val log = LoggerFactory.getLogger(getClass)

  def backwardsFrom(startItem: String): Future[String] = {
    client.backwards(startItem, 1000).flatMap { c =>
      c.items.headOption match {
        case Some(firstItem) =>
          CiriumMessageFormat.dateFromUri(firstItem).toOption match {
            case Some(dateTime) =>
              if (dateTime.getMillis <= targetTime.getMillis) {
                log.info(s"Reached back to ${dateTime.toDateTimeISO}. Will start processing forwards now")
                Future.successful(firstItem)
              } else {
                log.info(s"Reached back to ${dateTime.toDateTimeISO}. Aiming for ${targetTime.toDateTimeISO}")
                backwardsFrom(firstItem)
              }
            case None =>
              log.error(s"Failed to extract the date from $firstItem")
              metricsCollector.errorCounterMetric("backUntil-dateFromFirstItem")
              Future.failed(new Exception(s"Failed to extract the date from $firstItem"))
          }
        case None =>
          log.error("Failed to backfill: Cirium returned no previous feed items")
          metricsCollector.errorCounterMetric("backUntil-emptyItemList")
          Future.failed(new Exception("Failed to backfill: Cirium returned no previous feed items"))
      }
    }
  }
}
