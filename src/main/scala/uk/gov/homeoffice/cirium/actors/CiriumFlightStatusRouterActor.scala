package uk.gov.homeoffice.cirium.actors

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor.{ GetAllFlightDeltas, GetFlightDeltas }
import uk.gov.homeoffice.cirium.services.entities.CiriumFlightStatus

import scala.collection.mutable
import scala.util.Failure

object CiriumFlightStatusRouterActor {

  def props(portActors: Map[String, ActorRef]): Props = Props(classOf[CiriumFlightStatusRouterActor], portActors)

  case class GetFlightDeltas(flightId: Int)

  case object GetAllFlightDeltas

}

class CiriumFlightStatusRouterActor(portActors: Map[String, ActorRef]) extends Actor with ActorLogging {

  val flightDeltas: mutable.Map[Int, mutable.Seq[CiriumFlightStatus]] = mutable.Map()

  def receive: Receive = {

    case s: CiriumFlightStatus =>

      flightDeltas(s.flightId) = flightDeltas.getOrElse(s.flightId, mutable.Seq()) :+ s

      val portCodeForUpdate = s.arrivalAirportFsCode

      portActors.get(portCodeForUpdate).foreach(_ ! s)
    case GetFlightDeltas(flightId) =>
      val replyTo = sender()

      replyTo ! flightDeltas.getOrElse(flightId, List()).toList

    case GetAllFlightDeltas =>
      val replyTo = sender()

      replyTo ! flightDeltas

    case Failure(e) =>
      log.error(s"Got an exception", e)
    case other =>
      log.error(s"Got this unexpected message ${other}")
  }
}
