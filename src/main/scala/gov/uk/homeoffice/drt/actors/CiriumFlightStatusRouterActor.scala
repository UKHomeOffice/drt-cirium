package gov.uk.homeoffice.drt.actors

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import gov.uk.homeoffice.drt.services.entities.CiriumFlightStatus

import scala.util.Failure

object CiriumFlightStatusRouterActor {

  final case object GetStatuses

  def props(portActors: Map[String, ActorRef]): Props = Props(classOf[CiriumFlightStatusRouterActor], portActors)
}

class CiriumFlightStatusRouterActor(portActors: Map[String, ActorRef]) extends Actor with ActorLogging {

  def receive: Receive = {

    case s: CiriumFlightStatus =>
      val portCodeForUpdate = s.arrivalAirportFsCode

      portActors.get(portCodeForUpdate).foreach(_ ! s)

    case Failure(e) =>
      log.error(s"Got an exception", e)
    case other =>
      log.error(s"Got this unexpected message ${other}")
  }
}
