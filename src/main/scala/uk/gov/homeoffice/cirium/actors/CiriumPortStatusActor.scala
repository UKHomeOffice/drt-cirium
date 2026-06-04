package uk.gov.homeoffice.cirium.actors

import org.apache.pekko.actor.{Actor, Props, Timers}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.services.entities.CiriumTrackableStatus

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Failure

object CiriumPortStatusActor {

  /** Returns a plain list of flight statuses currently held in memory for this port. */
  final case object GetStatuses

  /** Returns full trackable status objects currently held in memory for this port. */
  final case object GetTrackableStatuses

  /** Returns feed-health metadata derived from this port actor's in-memory state. */
  final case object GetPortFeedHealthSummary

  /** Triggers removal of entries older than the configured history window. */
  final case object RemoveExpired

  /** Timer key for the periodic expiration task. */
  final case object TickKey

  /**
   * Creates a per-port actor that stores the latest known status per flight id.
   *
   * @param hoursOfHistory number of historical hours to keep in memory before expiry
   * @param currentTimeMillisFunc clock function injected for testability
   */
  def props(
    hoursOfHistory: Int = 24,
    currentTimeMillisFunc: () => Long = () => new DateTime().getMillis): Props =
    Props(new CiriumPortStatusActor(hoursOfHistory, currentTimeMillisFunc))
}

/** Metadata about the most recent expiration sweep. */
case class RemovalDetails(lastRemovalTime: Long, totalRemoved: Int, remainingAfterRemoval: Int)

/**
 * Health summary for a single port actor's feed cache.
 *
 * All values are derived from in-memory statuses currently retained by the actor.
 */
case class PortFeedHealthSummary(
  storedFlightStatuses: Int,
  oldestMessageSent: Option[Long],
  oldestMessageProcessed: Long,
  newestMessageSent: Option[Long],
  newestMessageProcessed: Long,
  lastRemoval: Option[RemovalDetails])

/**
 * Per-port in-memory cache of Cirium trackable statuses.
 *
 * The actor keeps one status per flight id, supports query messages from routes,
 * and periodically removes expired flights based on arrival time.
 */
class CiriumPortStatusActor(
  hoursOfHistory: Int,
  nowMillis: () => Long) extends Actor with Timers {
  private val log = LoggerFactory.getLogger(getClass)

  import CiriumPortStatusActor._

  /** Latest known trackable status by flight id. */
  val trackableStatuses: mutable.Map[Int, CiriumTrackableStatus] = mutable.Map[Int, CiriumTrackableStatus]()

  var latestStatus: Option[CiriumTrackableStatus] = None

  var removalDetails: Option[RemovalDetails] = None

  /** Retention period used by RemoveExpired sweeps. */
  val expireAfterMillis: Long = hoursOfHistory * 60 * 60 * 1000

  timers.startTimerAtFixedRate(TickKey, RemoveExpired, 60.seconds)

  def receive: Receive = {

    case GetStatuses =>
      val replyTo = sender()
      log.info(s"Sending ${trackableStatuses.size} flight statuses")
      replyTo ! trackableStatuses.values.map(_.status).toList

    case GetTrackableStatuses =>
      val replyTo = sender()
      log.info(s"Sending ${trackableStatuses.size} flight statuses")
      replyTo ! trackableStatuses.values.toList

    case GetPortFeedHealthSummary =>

      val summary = if (trackableStatuses.isEmpty)
        PortFeedHealthSummary(
          0,
          None,
          0L,
          None,
          0L,
          removalDetails)
      else {
        val oldestStatus = trackableStatuses.values.minBy(_.processedMillis)
        val newestStatus = trackableStatuses.values.maxBy(_.processedMillis)
        PortFeedHealthSummary(
          trackableStatuses.size,
          oldestStatus.messageIssuedAt,
          oldestStatus.processedMillis,
          newestStatus.messageIssuedAt,
          newestStatus.processedMillis,
          removalDetails)
      }

      sender() ! summary
    case RemoveExpired =>
      val expireAfter = nowMillis() - expireAfterMillis

      val forRemoval = trackableStatuses.collect {
        case (key, CiriumTrackableStatus(status, _, _)) if status.arrivalDate.millis < expireAfter =>
          key
      }

      val removals = RemovalDetails(System.currentTimeMillis(), forRemoval.size, trackableStatuses.size)

      if (removals.totalRemoved > 0) {
        log.info(s"Removing ${removals.totalRemoved} expired flights. ${removals.remainingAfterRemoval} flights remaining")
        removalDetails = Option(removals)
        trackableStatuses --= forRemoval
      }

    case s: CiriumTrackableStatus =>
      trackableStatuses(s.status.flightId) = s
      latestStatus = Option(s)

    case Failure(t) =>
      log.error(s"Got a failure", t)

    case other =>
      log.error(s"Got this unexpected message $other")
  }
}
