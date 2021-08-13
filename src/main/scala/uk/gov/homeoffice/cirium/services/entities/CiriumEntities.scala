package uk.gov.homeoffice.cirium.services.entities

import akka.http.scaladsl.model.Uri
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

//json Schema https://api.flightstats.com/flex/flightstatus/rest/v2/schema/json

case class CiriumInitialResponse(request: CiriumRequestMetaData, item: String) {
  def uri: Uri = Uri(item)
}
trait CiriumFlightStatusResponse

case class CiriumFlightStatusResponseSuccess(
  request: CiriumRequestMetaData,
  flightStatuses: Option[List[CiriumFlightStatus]]) extends CiriumFlightStatusResponse

case class CiriumFlightStatusResponseFailure(
  error: Throwable,
  timestamp: Long = System.currentTimeMillis) extends CiriumFlightStatusResponse

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

case class CiriumDelays(
  departureGateDelayMinutes: Option[Int],
  departureRunwayDelayMinutes: Option[Int],
  arrivalGateDelayMinutes: Option[Int],
  arrivalRunwayDelayMinutes: Option[Int])

case class CiriumFlightDurations(
  scheduledBlockMinutes: Option[Int],
  blockMinutes: Option[Int],
  scheduledAirMinutes: Option[Int],
  airMinutes: Option[Int],
  scheduledTaxiOutMinutes: Option[Int],
  taxiOutMinutes: Option[Int],
  scheduledTaxiInMinutes: Option[Int],
  taxiInMinutes: Option[Int])

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
  delays: Option[CiriumDelays],
  flightDurations: Option[CiriumFlightDurations],
  codeshares: Seq[CiriumCodeshare],
  airportResources: Option[CiriumAirportResources],
  flightStatusUpdates: Seq[CiriumFlightStatusUpdate])

case class CiriumTrackableStatus(status: CiriumFlightStatus, messageUri: String, processedMillis: Long) {

  def isInSync(threshold: FiniteDuration = 1 minute) = CiriumMessageFormat
    .dateFromUri(messageUri)
    .toOption
    .exists(issueDate => processedMillis - threshold.toMillis < issueDate.getMillis)

  def messageIssuedAt: Option[Long] = {
    CiriumMessageFormat.dateFromUri(messageUri).toOption.map(_.getMillis)
  }
}

object CiriumMessageFormat {

  def dateFromUri(uri: String): Try[DateTime] = Try {

    val dateBits = uri.split("json/").last.split("/").toList

    dateBits match {
      case year :: month :: day :: hour :: minute :: seconds :: _ =>
        new DateTime(year.toInt, month.toInt, day.toInt, hour.toInt, minute.toInt, seconds.toInt)
      case _ => throw new Exception(s"Url $uri is not parsable as a date-time.")
    }
  }

}
