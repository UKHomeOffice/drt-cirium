package uk.gov.homeoffice.cirium.services.entities

case class CiriumScheduledFlights(
  carrierFsCode: String,
  flightNumber: String,
  departureAirportFsCode: String,
  arrivalAirportFsCode: String,
  departureTime: String,
  arrivalTime: String)

case class CiriumScheduledResponse(scheduledFlights: Seq[CiriumScheduledFlights])

case class CiriumScheduledFlightRequest(
  flightCode: String,
  flightNumber: Int,
  year: Int,
  month: Int,
  day: Int)