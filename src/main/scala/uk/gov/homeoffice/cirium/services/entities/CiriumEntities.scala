package uk.gov.homeoffice.cirium.services.entities

import akka.http.scaladsl.model.Uri
import org.joda.time.DateTime

case class CiriumInitialResponse(request: CiriumRequestMetaData, item: String) {
  def uri = Uri(item)
}

case class CiriumFlightStatusResponse(request: CiriumRequestMetaData, flightStatuses: Option[List[CiriumFlightStatus]])

case class CiriumItemResponse(request: CiriumRequestMetaData, item: String)

case class CiriumItemListResponse(request: CiriumRequestMetaData, items: List[String])

case class CiriumBatchSize(requested: String, interpreted: Int)

case class CiriumRequestMetaData(
  endpoint: String,
  itemId: Option[CiriumItemId],
  batchSize: Option[CiriumBatchSize],
  url: String)

case class CiriumItemId(requested: String, interpreted: String)

case class CiriumDate(dateUtc: String, dateLocal: Option[String], millis: Long)

object CiriumDate {
  def apply(dateUtc: String, dateLocal: Option[String]): CiriumDate = CiriumDate(
    dateUtc,
    dateLocal,
    DateTime.parse(dateUtc).getMillis)
}

case class CiriumCodeshare(fsCode: String, flightNumber: String, relationship: String)

case class CiriumFlightStatusUpdate(updatedAt: CiriumDate, source: String)

case class CiriumAirportResources(
  departureTerminal: Option[String],
  departureGate: Option[String],
  arrivalTerminal: Option[String],
  arrivalGate: Option[String],
  baggage: Option[String])

case class CiriumOperationalTimes(
  publishedDeparture: Option[CiriumDate],
  scheduledGateDeparture: Option[CiriumDate],
  estimatedGateDeparture: Option[CiriumDate],
  actualGateDeparture: Option[CiriumDate],
  flightPlanPlannedDeparture: Option[CiriumDate],
  scheduledRunwayDeparture: Option[CiriumDate],
  estimatedRunwayDeparture: Option[CiriumDate],
  actualRunwayDeparture: Option[CiriumDate],
  publishedArrival: Option[CiriumDate],
  flightPlanPlannedArrival: Option[CiriumDate],
  scheduledGateArrival: Option[CiriumDate],
  estimatedGateArrival: Option[CiriumDate],
  actualGateArrival: Option[CiriumDate],
  scheduledRunwayArrival: Option[CiriumDate],
  estimatedRunwayArrival: Option[CiriumDate],
  actualRunwayArrival: Option[CiriumDate])

case class CiriumFlightStatus(
  flightId: Int,
  carrierFsCode: String,
  operatingCarrierFsCode: String,
  primaryCarrierFsCode: String,
  flightNumber: String,
  departureAirportFsCode: String,
  arrivalAirportFsCode: String,
  departureDate: CiriumDate,
  arrivalDate: CiriumDate,
  status: String,
  operationalTimes: CiriumOperationalTimes,
  codeshares: Seq[CiriumCodeshare],
  airportResources: Option[CiriumAirportResources],
  flightStatusUpdates: Seq[CiriumFlightStatusUpdate])
