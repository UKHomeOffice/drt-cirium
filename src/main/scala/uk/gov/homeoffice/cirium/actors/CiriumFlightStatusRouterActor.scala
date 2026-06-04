package uk.gov.homeoffice.cirium.actors

import org.apache.pekko.actor.{Actor, ActorRef, Props}
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor._
import uk.gov.homeoffice.cirium.services.entities.CiriumTrackableStatus

import java.lang.management.ManagementFactory
import scala.language.postfixOps
import scala.util.Failure

object CiriumFlightStatusRouterActor {

  /** Creates a router actor that dispatches messages to per-port actors. */
  def props(portActors: Map[String, ActorRef]): Props = Props(new CiriumFlightStatusRouterActor(portActors))

  /** Legacy query message for per-flight delta retrieval. */
  case class GetFlightDeltas(flightId: Int)

  /** Legacy query message for retrieving all deltas. */
  case object GetAllFlightDeltas

  /** Returns whether the feed has caught up and is in-sync. */
  case object GetReadiness

  /** Returns router-level feed health information. */
  case object GetHealth

  /** JVM uptime in seconds used in health responses. */
  def upTimeSeconds: Long = ManagementFactory.getRuntimeMXBean.getUptime / 1000

}

/**
 * Health payload for the Cirium feed router.
 *
 * @param isReady true once at least one in-sync message has been observed
 * @param lastMessage the most recent message seen by the router
 * @param upTime process uptime in seconds
 */
case class CiriumFeedHealthStatus(
  isReady: Boolean,
  lastMessage: Option[CiriumTrackableStatus],
  upTime: Long)

/**
 * Receives feed updates and routes them to per-port actors using arrival airport code.
 *
 * The actor also tracks feed readiness and the latest processed message for health endpoints.
 */
class CiriumFlightStatusRouterActor(portActors: Map[String, ActorRef]) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)

  var isReady: Boolean = false

  var lastMessage: Option[CiriumTrackableStatus] = None

  def receive: Receive = {

    case GetReadiness =>
      sender() ! isReady

    case GetHealth =>

      sender() ! CiriumFeedHealthStatus(isReady, lastMessage, upTimeSeconds)

    case ts: CiriumTrackableStatus =>
      if (!isReady && ts.isInSync()) {
        isReady = true
        log.info(s"Finished cirium backlog after $upTimeSeconds seconds.")
      }

      lastMessage = Option(ts)

      val portCodeForUpdate = ts.status.arrivalAirportFsCode
      portActors.get(portCodeForUpdate).foreach(_ ! ts)

    case Failure(t) =>
      log.error(s"Got a failure", t)

    case other =>
      log.error(s"Got this unexpected message ${other.getClass}")
  }

}
