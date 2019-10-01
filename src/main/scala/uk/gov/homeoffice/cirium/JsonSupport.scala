package uk.gov.homeoffice.cirium

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat }
import uk.gov.homeoffice.cirium.services.entities._

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ciriumRequestMetaJsonFormat: RootJsonFormat[CiriumItemId] = jsonFormat2(CiriumItemId)
  implicit val ciriumDateJsonFormat: RootJsonFormat[CiriumDate] = CiriumDateProtocol.CiriumDateFormat
  implicit val ciriumFlightStatusUpdateJsonFormat: RootJsonFormat[CiriumFlightStatusUpdate] = jsonFormat2(CiriumFlightStatusUpdate)
  implicit val ciriumBatchSizeJsonFormat: RootJsonFormat[CiriumBatchSize] = jsonFormat2(CiriumBatchSize)
  implicit val ciriumItemIdJsonFormat: RootJsonFormat[CiriumRequestMetaData] = jsonFormat4(CiriumRequestMetaData)
  implicit val ciriumResponseJsonFormat: RootJsonFormat[CiriumInitialResponse] = jsonFormat2(CiriumInitialResponse)
  implicit val ciriumItemsResponseJsonFormat: RootJsonFormat[CiriumItemListResponse] = jsonFormat2(CiriumItemListResponse)
  implicit val ciriumOperationalTimesJsonFormat: RootJsonFormat[CiriumOperationalTimes] = jsonFormat16(CiriumOperationalTimes)
  implicit val ciriumCodesharesJsonFormat: RootJsonFormat[CiriumCodeshare] = jsonFormat3(CiriumCodeshare)
  implicit val ciriumAirportResourcesJsonFormat: RootJsonFormat[CiriumAirportResources] = jsonFormat5(CiriumAirportResources)
  implicit val ciriumFlightStatusJsonFormat: RootJsonFormat[CiriumFlightStatus] = jsonFormat14(CiriumFlightStatus)
  implicit val ciriumFlightStatusResponseJsonFormat: RootJsonFormat[CiriumFlightStatusResponse] = jsonFormat2(CiriumFlightStatusResponse)
  implicit val ciriumTrackableStatusJsonFormat: RootJsonFormat[CiriumTrackableStatus] = jsonFormat3(CiriumTrackableStatus)
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
