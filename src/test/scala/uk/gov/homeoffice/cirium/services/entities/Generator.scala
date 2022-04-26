package uk.gov.homeoffice.cirium.services.entities

object Generator {
  def ciriumFlightStatus(sch: String = "2021-07-15T11:05:00.000Z",
                         estRunway: String = "",
                         actRunway: String = "",
                         estGate: String = "",
                         actGate: String = "",
                        ): CiriumFlightStatus = CiriumFlightStatus(
    flightId = 100000,
    carrierFsCode = "",
    operatingCarrierFsCode = "",
    primaryCarrierFsCode = "",
    flightNumber = "",
    departureAirportFsCode = "",
    arrivalAirportFsCode = "",
    departureDate = CiriumDate("2019-07-15T09:10:00.000Z", None),
    arrivalDate = CiriumDate(sch, None),
    status = "",
    operationalTimes = CiriumOperationalTimes(
      publishedDeparture = None,
      scheduledGateDeparture = None,
      estimatedGateDeparture = None,
      actualGateDeparture = None,
      flightPlanPlannedDeparture = None,
      scheduledRunwayDeparture = None,
      estimatedRunwayDeparture = None,
      actualRunwayDeparture = None,
      publishedArrival = None,
      flightPlanPlannedArrival = None,
      scheduledGateArrival = None,
      estimatedGateArrival = if (estGate.nonEmpty) Option(CiriumDate(estGate)) else None,
      actualGateArrival = if (actGate.nonEmpty) Option(CiriumDate(actGate)) else None,
      scheduledRunwayArrival = Option(CiriumDate(sch)),
      estimatedRunwayArrival = if (estRunway.nonEmpty) Option(CiriumDate(estRunway)) else None,
      actualRunwayArrival = if (actRunway.nonEmpty) Option(CiriumDate(actRunway)) else None),
    delays = None,
    flightDurations = None,
    codeshares = List(),
    airportResources = None,
    flightStatusUpdates = Seq()
  )

}
