package gov.uk.homeoffice.drt.services.entities

import akka.http.scaladsl.model.Uri

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

case class CiriumDate(dateUtc: String, dateLocal: Option[String])

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
  estimatedRunwayDeparture: Option[CiriumDate],
  actualRunwayDeparture: Option[CiriumDate],
  publishedArrival: Option[CiriumDate],
  scheduledGateArrival: Option[CiriumDate])

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

