package uk.gov.homeoffice.cirium

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat }
import uk.gov.homeoffice.cirium.actors.{ CiriumFeedHealthStatus, PortFeedHealthSummary, RemovalDetails }
import uk.gov.homeoffice.cirium.services.entities.{ CiriumScheduledFlightRequest, CiriumScheduledResponse, _ }
import uk.gov.homeoffice.cirium.services.health.CiriumAppHealthSummary

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ciriumItemIdJsonFormat: RootJsonFormat[CiriumItemId] = jsonFormat2(CiriumItemId)
  implicit val ciriumDateJsonFormat: RootJsonFormat[CiriumDate] = CiriumDateProtocol.CiriumDateFormat
  implicit val ciriumFlightDurationsJsonFormat: RootJsonFormat[CiriumFlightDurations] = jsonFormat8(CiriumFlightDurations)
  implicit val ciriumDelaysJsonFormat: RootJsonFormat[CiriumDelays] = jsonFormat4(CiriumDelays)
  implicit val ciriumFlightStatusUpdateJsonFormat: RootJsonFormat[CiriumFlightStatusUpdate] = jsonFormat2(CiriumFlightStatusUpdate)
  implicit val ciriumBatchSizeJsonFormat: RootJsonFormat[CiriumBatchSize] = jsonFormat2(CiriumBatchSize)
  implicit val ciriumRequestMetaJsonFormat: RootJsonFormat[CiriumRequestMetaData] = jsonFormat4(CiriumRequestMetaData)
  implicit val ciriumResponseJsonFormat: RootJsonFormat[CiriumInitialResponse] = jsonFormat2(CiriumInitialResponse)
  implicit val ciriumItemsResponseJsonFormat: RootJsonFormat[CiriumItemListResponse] = jsonFormat2(CiriumItemListResponse)
  implicit val ciriumOperationalTimesJsonFormat: RootJsonFormat[CiriumOperationalTimes] = jsonFormat16(CiriumOperationalTimes)
  implicit val ciriumCodesharesJsonFormat: RootJsonFormat[CiriumCodeshare] = jsonFormat3(CiriumCodeshare)
  implicit val ciriumAirportResourcesJsonFormat: RootJsonFormat[CiriumAirportResources] = jsonFormat5(CiriumAirportResources)
  implicit val ciriumFlightStatusJsonFormat: RootJsonFormat[CiriumFlightStatus] = jsonFormat16(CiriumFlightStatus)
  implicit val ciriumFlightStatusResponseJsonFormat: RootJsonFormat[CiriumFlightStatusResponseSuccess] = jsonFormat2(CiriumFlightStatusResponseSuccess)
  implicit val ciriumTrackableStatusJsonFormat: RootJsonFormat[CiriumTrackableStatus] = jsonFormat3(CiriumTrackableStatus)

  implicit val ciriumFeedHealthStatusJsonFormat: RootJsonFormat[CiriumFeedHealthStatus] = jsonFormat3(CiriumFeedHealthStatus)
  implicit val removalDetailsJsonFormat: RootJsonFormat[RemovalDetails] = jsonFormat3(RemovalDetails)
  implicit val portFeedHealthSummaryJsonFormat: RootJsonFormat[PortFeedHealthSummary] = jsonFormat6(PortFeedHealthSummary)
  implicit val ciriumAppHealthSummaryJsonFormat: RootJsonFormat[CiriumAppHealthSummary] = jsonFormat2(CiriumAppHealthSummary)

  implicit val ciriumScheduledFlightsFormats = jsonFormat6(CiriumScheduledFlights)
  implicit val ciriumScheduledResponseFormats = jsonFormat1(CiriumScheduledResponse)
  implicit val ciriumScheduledArrivalRequestFormats = jsonFormat5(CiriumScheduledFlightRequest)
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
