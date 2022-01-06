package uk.gov.homeoffice.cirium

import akka.actor.Scheduler
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.specs2.mutable.Specification
import uk.gov.homeoffice.cirium.services.api.FlightScheduledRoutes
import uk.gov.homeoffice.cirium.services.entities.{CiriumScheduledFlightRequest, CiriumScheduledFlights, CiriumScheduledResponse}
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

class FlightScheduledRoutesSpec extends Specification with FlightScheduledRoutes with Specs2RouteTest {

  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val scheduler: Scheduler = system.scheduler
  val executionContext: ExecutionContext = system.dispatcher

  import uk.gov.homeoffice.cirium.JsonSupport._

  val ciriumRespondJson: String = Source.fromResource("ciriumScheduledFlight.json").getLines().mkString

  def expectedScheduleResponse = List(
    CiriumScheduledFlights(
      arrivalAirportFsCode = "LTN",
      arrivalTime = "2021-01-21T20:55:00.000",
      carrierFsCode = "ASD",
      departureAirportFsCode = "KIV",
      departureTime = "2021-01-21T19:25:00.000",
      flightNumber = "23456"))

  def httpResponse(jsonString: String): HttpResponse = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, jsonString))

  "Given json string" should {
    "UnMarshal httpResponse to CiriumScheduledResponse object" in {
      val futureResult = Unmarshal[HttpResponse](httpResponse(ciriumRespondJson)).to[CiriumScheduledResponse]
      val result = Await.result(futureResult, 5.second)
      result.scheduledFlights mustEqual expectedScheduleResponse
    }

  }
  val client: Cirium.Client = new MockClient(ciriumRespondJson)

  "flightScheduled route for a specific flight" should {
    "respond with the flight details with departure date" in {
      val scheduledArrival = CiriumScheduledFlightRequest("W9", 3797, 2021, 1, 21)
      val scheduledArrivalEntityF = Marshal[CiriumScheduledFlightRequest](scheduledArrival).to[MessageEntity]
      val scheduledArrivalEntity = Await.result(scheduledArrivalEntityF, 5.second)
      Get("/flightScheduled", scheduledArrivalEntity) ~> flightScheduledRoute ~> check {
        status mustEqual StatusCodes.OK
        val futureResult = Unmarshal[HttpResponse](httpResponse(ciriumRespondJson)).to[CiriumScheduledResponse]
        val result = Await.result(futureResult, 5.second)
        result.scheduledFlights mustEqual expectedScheduleResponse

      }
    }
  }

}
