package uk.gov.homeoffice.cirium.actors

import akka.actor.{ Actor, ActorLogging, Props, Timers }
import org.joda.time.DateTime
import uk.gov.homeoffice.cirium.services.entities.CiriumTrackableStatus

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Failure

object CiriumPortStatusActor {

  final case object GetStatuses

  final case object GetTrackableStatuses

  final case object GetPortFeedHealthSummary

  final case object RemoveExpired

  final case object TickKey

  def props(
    hoursOfHistory: Int = 24,
    currentTimeMillisFunc: () => Long = () => new DateTime().getMillis): Props =
    Props(classOf[CiriumPortStatusActor], hoursOfHistory, currentTimeMillisFunc)
}

case class RemovalDetails(lastRemovalTime: Long, totalRemoved: Int, remainingAfterRemoval: Int)

case class PortFeedHealthSummary(
  storedFlightStatuses: Int,
  oldestMessageSent: Option[Long],
  oldestMessageProcessed: Long,
  newestMessageSent: Option[Long],
  newestMessageProcessed: Long,
  lastRemoval: Option[RemovalDetails])

class CiriumPortStatusActor(
  hoursOfHistory: Int,
  nowMillis: () => Long) extends Actor with ActorLogging with Timers {

  import CiriumPortStatusActor._

  val trackableStatuses: mutable.Map[Int, CiriumTrackableStatus] = mutable.Map[Int, CiriumTrackableStatus]()

  var latestStatus: Option[CiriumTrackableStatus] = None

  var removalDetails: Option[RemovalDetails] = None

  val expireAfterMillis: Long = hoursOfHistory * 60 * 60 * 1000

  timers.startTimerAtFixedRate(TickKey, RemoveExpired, 10.seconds)

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

      log.info(s"Removing ${removals.totalRemoved} expired flight statuses out of ${removals.remainingAfterRemoval}")

      removalDetails = Option(removals)
      trackableStatuses --= forRemoval

    case s: CiriumTrackableStatus =>
      trackableStatuses(s.status.flightId) = s
      latestStatus = Option(s)

    case Failure(t) =>
      log.error(t, s"Got a failure")

    case other =>
      log.error(s"Got this unexpected message $other")
  }
}
