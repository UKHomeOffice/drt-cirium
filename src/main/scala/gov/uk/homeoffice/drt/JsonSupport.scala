package gov.uk.homeoffice.drt

import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import gov.uk.homeoffice.drt.services.entities._
import spray.json.DefaultJsonProtocol

object JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ciriumRequestMetaJsonFormat: RootJsonFormat[CiriumItemId] = jsonFormat2(CiriumItemId)
  implicit val ciriumDateJsonFormat: RootJsonFormat[CiriumDate] = jsonFormat2(CiriumDate)
  implicit val ciriumFlightStatusUpdateJsonFormat: RootJsonFormat[CiriumFlightStatusUpdate] = jsonFormat2(CiriumFlightStatusUpdate)
  implicit val ciriumBatchSizeJsonFormat: RootJsonFormat[CiriumBatchSize] = jsonFormat2(CiriumBatchSize)
  implicit val ciriumItemIdJsonFormat: RootJsonFormat[CiriumRequestMetaData] = jsonFormat4(CiriumRequestMetaData)
  implicit val ciriumResponseJsonFormat: RootJsonFormat[CiriumInitialResponse] = jsonFormat2(CiriumInitialResponse)
  implicit val ciriumItemsResponseJsonFormat: RootJsonFormat[CiriumItemListResponse] = jsonFormat2(CiriumItemListResponse)
  implicit val ciriumOperationalTimesJsonFormat: RootJsonFormat[CiriumOperationalTimes] = jsonFormat6(CiriumOperationalTimes)
  implicit val ciriumCodesharesJsonFormat: RootJsonFormat[CiriumCodeshare] = jsonFormat3(CiriumCodeshare)
  implicit val ciriumAirportResourcesJsonFormat: RootJsonFormat[CiriumAirportResources] = jsonFormat5(CiriumAirportResources)
  implicit val ciriumFlightStatusJsonFormat: RootJsonFormat[CiriumFlightStatus] = jsonFormat14(CiriumFlightStatus)
  implicit val ciriumFlightStatusResponseJsonFormat: RootJsonFormat[CiriumFlightStatusResponse] = jsonFormat2(CiriumFlightStatusResponse)
}
