package uk.gov.homeoffice.cirium.services.entities

import org.apache.pekko.http.scaladsl.model.Uri
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import scala.util.Try

//json Schema https://api.flightstats.com/flex/flightstatus/rest/v2/schema/json

/** Response envelope returned by the Cirium `latest` endpoint. */
case class CiriumInitialResponse(request: CiriumRequestMetaData, item: String) {
  def uri: Uri = Uri(item)
}

/** Marker trait for successful and failed item fetch outcomes. */
trait CiriumFlightStatusResponse

/** Parsed flight-status payload for a single feed item. */
case class CiriumFlightStatusResponseSuccess(request: CiriumRequestMetaData,
                                             flightStatuses: Option[List[CiriumFlightStatus]]) extends CiriumFlightStatusResponse

/** Failure wrapper used when a feed item cannot be fetched or parsed. */
case class CiriumFlightStatusResponseFailure(error: Throwable,
                                             timestamp: Long = System.currentTimeMillis) extends CiriumFlightStatusResponse

case class CiriumItemResponse(request: CiriumRequestMetaData, item: String)

/** List of feed item URLs returned by a previous/next page call. */
case class CiriumItemListResponse(items: List[String])

/** Helpers for [[CiriumItemListResponse]]. */
object CiriumItemListResponse {
  val empty: CiriumItemListResponse = CiriumItemListResponse(List())
}

case class CiriumBatchSize(requested: String, interpreted: Int)

case class CiriumRequestMetaData(endpoint: String,
                                 itemId: Option[CiriumItemId],
                                 batchSize: Option[CiriumBatchSize],
                                 url: String)

case class CiriumItemId(requested: String, interpreted: String)

/** Cirium date representation with UTC/local strings and epoch millis. */
case class CiriumDate(dateUtc: String, dateLocal: Option[String], millis: Long)

/** Constructors for creating [[CiriumDate]] from strings. */
object CiriumDate {
  def apply(dateUtc: String, dateLocal: Option[String]): CiriumDate = CiriumDate(
    dateUtc,
    dateLocal,
    DateTime.parse(dateUtc).getMillis)

  def apply(dateStr: String): CiriumDate = {
    val date = DateTime.parse(dateStr)
    val localDate = ISODateTimeFormat.dateTime().print(date.withZone(DateTimeZone.forID("Europe/London")))
    CiriumDate(dateStr, Option(localDate), date.getMillis)
  }
}

case class CiriumCodeshare(fsCode: String, flightNumber: String, relationship: String)

case class CiriumDelays(departureGateDelayMinutes: Option[Int],
                        departureRunwayDelayMinutes: Option[Int],
                        arrivalGateDelayMinutes: Option[Int],
                        arrivalRunwayDelayMinutes: Option[Int])

case class CiriumFlightDurations(scheduledBlockMinutes: Option[Int],
                                 blockMinutes: Option[Int],
                                 scheduledAirMinutes: Option[Int],
                                 airMinutes: Option[Int],
                                 scheduledTaxiOutMinutes: Option[Int],
                                 taxiOutMinutes: Option[Int],
                                 scheduledTaxiInMinutes: Option[Int],
                                 taxiInMinutes: Option[Int])

case class CiriumFlightStatusUpdate(updatedAt: CiriumDate, source: String)

case class CiriumAirportResources(departureTerminal: Option[String],
                                  departureGate: Option[String],
                                  arrivalTerminal: Option[String],
                                  arrivalGate: Option[String],
                                  baggage: Option[String])

case class CiriumOperationalTimes(publishedDeparture: Option[CiriumDate],
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

case class CiriumStatusSchedule(flightType: String)

/** Constants and helpers for Cirium schedule flight types. */
object CiriumStatusSchedule {
  val ciriumFreightFlightTypes = Set("F", "V", "M", "A", "H")

  def passengerFlight: CiriumStatusSchedule = CiriumStatusSchedule("J")
  def freightFlight: CiriumStatusSchedule = CiriumStatusSchedule("F")
}

/** Normalized Cirium flight status used throughout the application. */
case class CiriumFlightStatus(flightId: Int,
                              carrierFsCode: String,
                              operatingCarrierFsCode: String,
                              primaryCarrierFsCode: String,
                              flightNumber: String,
                              departureAirportFsCode: String,
                              arrivalAirportFsCode: String,
                              departureDate: CiriumDate,
                              arrivalDate: CiriumDate,
                              status: String,
                              schedule: CiriumStatusSchedule,
                              operationalTimes: CiriumOperationalTimes,
                              delays: Option[CiriumDelays],
                              flightDurations: Option[CiriumFlightDurations],
                              codeshares: Seq[CiriumCodeshare],
                              airportResources: Option[CiriumAirportResources],
                              flightStatusUpdates: Seq[CiriumFlightStatusUpdate]) {
  lazy val estimated: Option[Long] = {
    (operationalTimes.estimatedRunwayArrival, operationalTimes.estimatedGateArrival) match {
      case (Some(CiriumDate(_, _, estMillis)), _) if estMillis != arrivalDate.millis => Option(estMillis)
      case (_, Some(CiriumDate(_, _, estChox))) if estChox != arrivalDate.millis => Option(estChox - (5 * 60 * 1000))
      case _ => None
    }
  }

  lazy val actualTouchdown: Option[Long] = operationalTimes.actualRunwayArrival.map(_.millis)

  lazy val estimatedChox: Option[Long] = {
    (operationalTimes.actualRunwayArrival, operationalTimes.estimatedGateArrival) match {
      case (Some(_), Some(CiriumDate(_, _, estChox))) =>
        Option(estChox)
      case _ =>
        None
    }
  }

  lazy val actualChox: Option[Long] = operationalTimes.actualGateArrival.map(_.millis)
}

/**
 * Flight status decorated with feed metadata used for routing and health checks.
 */
case class CiriumTrackableStatus(status: CiriumFlightStatus, messageUri: String, processedMillis: Long) {
  def isInSync(threshold: FiniteDuration = 1 minute): Boolean = CiriumMessageFormat
    .dateFromUri(messageUri)
    .toOption
    .exists(issueDate => processedMillis - threshold.toMillis < issueDate.getMillis)

  def messageIssuedAt: Option[Long] = {
    CiriumMessageFormat.dateFromUri(messageUri).toOption.map(_.getMillis)
  }
}

/** Utilities for extracting timestamps from Cirium message URLs. */
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
