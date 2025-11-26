package uk.gov.homeoffice.cirium

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}
import uk.gov.homeoffice.cirium.actors.{CiriumFeedHealthStatus, PortFeedHealthSummary, RemovalDetails}
import uk.gov.homeoffice.cirium.services.entities._
import uk.gov.homeoffice.cirium.services.health.CiriumAppHealthSummary

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ciriumItemIdJsonFormat: RootJsonFormat[CiriumItemId] = jsonFormat2(CiriumItemId)
  implicit val ciriumDateJsonFormat: RootJsonFormat[CiriumDate] = CiriumDateProtocol.CiriumDateFormat
  implicit val ciriumFlightDurationsJsonFormat: RootJsonFormat[CiriumFlightDurations] = jsonFormat8(CiriumFlightDurations)
  implicit val ciriumDelaysJsonFormat: RootJsonFormat[CiriumDelays] = jsonFormat4(CiriumDelays)
  implicit val ciriumFlightStatusUpdateJsonFormat: RootJsonFormat[CiriumFlightStatusUpdate] = jsonFormat2(CiriumFlightStatusUpdate)
  implicit val ciriumBatchSizeJsonFormat: RootJsonFormat[CiriumBatchSize] = jsonFormat2(CiriumBatchSize)
  implicit val ciriumRequestMetaDataJsonFormat: RootJsonFormat[CiriumRequestMetaData] = jsonFormat4(CiriumRequestMetaData)
  implicit val ciriumResponseJsonFormat: RootJsonFormat[CiriumInitialResponse] = jsonFormat2(CiriumInitialResponse)
  implicit val ciriumItemsResponseJsonFormat: RootJsonFormat[CiriumItemListResponse] = jsonFormat1(CiriumItemListResponse.apply)
  implicit val ciriumOperationalTimesJsonFormat: RootJsonFormat[CiriumOperationalTimes] = jsonFormat16(CiriumOperationalTimes)
  implicit val ciriumCodeshareJsonFormat: RootJsonFormat[CiriumCodeshare] = jsonFormat3(CiriumCodeshare)
  implicit val ciriumAirportResourcesJsonFormat: RootJsonFormat[CiriumAirportResources] = jsonFormat5(CiriumAirportResources)
  implicit val ciriumStatusScheduleJsonFormat: RootJsonFormat[CiriumStatusSchedule] = jsonFormat(
    CiriumStatusSchedule.apply,
    "flightType",
  )
  implicit val ciriumFlightStatusJsonFormat: RootJsonFormat[CiriumFlightStatus] = jsonFormat(
    CiriumFlightStatus.apply,
    "flightId",
    "carrierFsCode",
    "operatingCarrierFsCode",
    "primaryCarrierFsCode",
    "flightNumber",
    "departureAirportFsCode",
    "arrivalAirportFsCode",
    "departureDate",
    "arrivalDate",
    "status",
    "schedule",
    "operationalTimes",
    "delays",
    "flightDurations",
    "codeshares",
    "airportResources",
    "flightStatusUpdates",
  )
  implicit val ciriumFlightStatusResponseJsonFormat: RootJsonFormat[CiriumFlightStatusResponseSuccess] = jsonFormat2(CiriumFlightStatusResponseSuccess)
  implicit val ciriumTrackableStatusJsonFormat: RootJsonFormat[CiriumTrackableStatus] = jsonFormat3(CiriumTrackableStatus)

  implicit val ciriumFeedHealthStatusJsonFormat: RootJsonFormat[CiriumFeedHealthStatus] = jsonFormat3(CiriumFeedHealthStatus)
  implicit val removalDetailsJsonFormat: RootJsonFormat[RemovalDetails] = jsonFormat3(RemovalDetails)
  implicit val portFeedHealthSummaryJsonFormat: RootJsonFormat[PortFeedHealthSummary] = jsonFormat6(PortFeedHealthSummary)
  implicit val ciriumAppHealthSummaryJsonFormat: RootJsonFormat[CiriumAppHealthSummary] = jsonFormat2(CiriumAppHealthSummary)

  implicit val ciriumScheduledFlightsJsonFormats: RootJsonFormat[CiriumScheduledFlights] = jsonFormat6(CiriumScheduledFlights)
  implicit val ciriumScheduledResponseJsonFormats: RootJsonFormat[CiriumScheduledResponse] = jsonFormat1(CiriumScheduledResponse)
  implicit val ciriumScheduledArrivalRequestJsonFormats: RootJsonFormat[CiriumScheduledFlightRequest] = jsonFormat5(CiriumScheduledFlightRequest)
}

object CiriumDateProtocol extends DefaultJsonProtocol {

  implicit object CiriumDateFormat extends RootJsonFormat[CiriumDate] {
    def write(cd: CiriumDate) =

      JsObject(
        "dateUtc" -> JsString(cd.dateUtc),
        "dateLocal" -> JsString(cd.dateLocal.getOrElse("")),
        "millis" -> JsNumber(cd.millis))

    def read(value: JsValue) = value.asJsObject.getFields("dateUtc", "dateLocal") match {
      case Seq(JsString(dateUtc), JsString(dateLocal)) => CiriumDate(dateUtc, Option(dateLocal))
      case Seq(JsString(dateUtc)) => CiriumDate(dateUtc, None)
    }
  }

}
