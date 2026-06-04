package uk.gov.homeoffice.cirium.services.entities

/**
 * Scheduled-flight details returned from Cirium for a specific service/date query.
 */
case class CiriumScheduledFlights(
  carrierFsCode: String,
  flightNumber: String,
  departureAirportFsCode: String,
  arrivalAirportFsCode: String,
  departureTime: String,
  arrivalTime: String)

/** Envelope for scheduled-flight lookup responses. */
case class CiriumScheduledResponse(scheduledFlights: Seq[CiriumScheduledFlights])

/**
 * Input parameters used to request scheduled-flight data from Cirium.
 */
case class CiriumScheduledFlightRequest(
  flightCode: String,
  flightNumber: Int,
  year: Int,
  month: Int,
  day: Int)