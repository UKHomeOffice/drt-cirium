package uk.gov.homeoffice.cirium.services.api

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.services.entities.{CiriumScheduledFlightRequest, CiriumScheduledResponse}

import scala.concurrent.Future

trait FlightScheduledRoutes extends CiriumBaseRoutes {

  import uk.gov.homeoffice.cirium.JsonSupport._

  private val logger = LoggerFactory.getLogger(getClass)

  val scheduleApiEndpoint = "https://api.flightstats.com/flex/schedules/rest/v1/json/flight"

  val flightScheduledRoute: Route = pathPrefix("flightScheduled") {
    pathEndOrSingleSlash {
      get {
        entity(as[CiriumScheduledFlightRequest]) { csfRequest =>
          complete(
            client
              .makeRequest(
                endpoint = s"$scheduleApiEndpoint/${csfRequest.flightCode}/${csfRequest.flightNumber}/departing/${csfRequest.year}/${csfRequest.month}/${csfRequest.day}",
                maybeMaxRetries = Option(0))
              .flatMap(res => {
                val futureStatusResponse: Future[CiriumScheduledResponse] = Unmarshal[HttpResponse](res).to[CiriumScheduledResponse]
                futureStatusResponse.map { statusResponse =>
                  logger.info(s"statusResponse $statusResponse for cirium ScheduledFlightRequest $csfRequest")
                }
                futureStatusResponse
              }))
        }

      }
    }
  }

}
