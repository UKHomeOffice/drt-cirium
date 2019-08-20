package gov.uk.homeoffice.drt

import akka.actor.{ Actor, ActorLogging, Props }
import gov.uk.homeoffice.drt.services.entities.CiriumFlightStatus

object CiriumFlightStatusActor {

  final case object GetStatuses

  def props: Props = Props[CiriumFlightStatusActor]
}

class CiriumFlightStatusActor extends Actor with ActorLogging {
  import CiriumFlightStatusActor._

  var statuses = Set.empty[CiriumFlightStatus]

  def receive: Receive = {
    case GetStatuses =>
      sender() ! statuses

    case s: CiriumFlightStatus =>
      statuses += s
  }
}

