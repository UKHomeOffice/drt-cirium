package uk.gov.homeoffice.cirium.services.api

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.Logger
import uk.gov.homeoffice.cirium.services.entities.{ CiriumScheduledFlightRequest, CiriumScheduledResponse }

import scala.concurrent.Future

trait FlightScheduledRoutes extends CiriumBaseRoutes {

  import uk.gov.homeoffice.cirium.JsonSupport._

  val logger = Logger(getClass)

  val scheduleApiEndpoint = "https://api.flightstats.com/flex/schedules/rest/v1/json/flight"

  val flightScheduledRoute = pathPrefix("flightScheduled") {
    pathEndOrSingleSlash {
      get {
        entity(as[CiriumScheduledFlightRequest]) { csfRequest =>
          complete(
            client.makeRequest(s"$scheduleApiEndpoint/${csfRequest.flightCode}/${csfRequest.flightNumber}/departing/${csfRequest.year}/${csfRequest.month}/${csfRequest.day}")
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
