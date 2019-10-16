package uk.gov.homeoffice.cirium.actors

import akka.actor.{ Actor, ActorLogging, Props, Timers }
import org.joda.time.DateTime
import uk.gov.homeoffice.cirium.services.entities.CiriumTrackableStatus

import scala.collection.mutable
import scala.concurrent.duration._

object CiriumPortStatusActor {

  final case object GetStatuses

  final case object GetTrackableStatuses

  final case object RemoveExpired

  final case object TickKey

  def props(
    hoursOfHistory: Int = 24,
    currentTimeMillisFunc: () => Long = () => new DateTime().getMillis): Props = Props(classOf[CiriumPortStatusActor], hoursOfHistory, currentTimeMillisFunc)
}

class CiriumPortStatusActor(
  hoursOfHistory: Int,
  nowMillis: () => Long) extends Actor with ActorLogging with Timers {

  import CiriumPortStatusActor._

  val trackableStatuses: mutable.Map[Int, CiriumTrackableStatus] = mutable.Map[Int, CiriumTrackableStatus]()

  val expireAfterMillis: Long = hoursOfHistory * 60 * 60 * 1000

  timers.startPeriodicTimer(TickKey, RemoveExpired, 1 minutes)

  def receive: Receive = {
    case GetStatuses =>
      val replyTo = sender()
      log.info(s"Sending ${trackableStatuses.size} flight statuses")
      replyTo ! trackableStatuses.values.map(_.status).toList

    case GetTrackableStatuses =>
      val replyTo = sender()
      log.info(s"Sending ${trackableStatuses.size} flight statuses")
      replyTo ! trackableStatuses.values.toList

    case RemoveExpired =>
      val expireAfter = nowMillis() - expireAfterMillis

      val forRemoval = trackableStatuses.collect {
        case (key, CiriumTrackableStatus(status, _, _)) if status.arrivalDate.millis < expireAfter =>
          key
      }

      log.info(s"Removing ${forRemoval.size} expired flight statuses out of ${trackableStatuses.size}")

      trackableStatuses --= forRemoval

    case s: CiriumTrackableStatus =>
      trackableStatuses(s.status.flightId) = s

    case other =>
      log.error(s"Got this unexpected message ${other}")
  }
}
