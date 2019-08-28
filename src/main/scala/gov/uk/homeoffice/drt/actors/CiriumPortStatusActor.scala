package gov.uk.homeoffice.drt.actors

import akka.actor.{ Actor, ActorLogging, Props }
import gov.uk.homeoffice.drt.services.entities.CiriumFlightStatus

import scala.collection.mutable

object CiriumPortStatusActor {
  def props = Props[CiriumPortStatusActor]
}

class CiriumPortStatusActor() extends Actor with ActorLogging {

  import CiriumFlightStatusRouterActor._

  val statuses: mutable.Map[Int, CiriumFlightStatus] = mutable.Map[Int, CiriumFlightStatus]()

  def receive: Receive = {
    case GetStatuses =>
      val replyTo = sender()
      log.info(s"Sending ${statuses.size} flight statuses")
      replyTo ! statuses.values.toList

    case s: CiriumFlightStatus =>
      statuses(s.flightId) = s

    case other =>
      log.error(s"Got this unexpected message ${other}")
  }
}
