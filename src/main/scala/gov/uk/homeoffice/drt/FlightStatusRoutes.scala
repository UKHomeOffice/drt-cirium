package gov.uk.homeoffice.drt

import akka.actor.{ ActorSystem, Props }
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{ AskableActorRef, ask }
import akka.util.Timeout
import gov.uk.homeoffice.drt.CiriumFlightStatusActor._
import gov.uk.homeoffice.drt.services.entities.CiriumFlightStatus

import scala.concurrent.Future
import scala.concurrent.duration._

trait FlightStatusRoutes {

  import JsonSupport._

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[FlightStatusRoutes])

  implicit lazy val timeout = Timeout(5.seconds)

  val ciriumFlightStatusActor: AskableActorRef = system.actorOf(Props[CiriumFlightStatusActor])

  lazy val userRoutes: Route =
    pathPrefix("statuses") {
      concat(
        pathEnd {
          concat(
            get {
              val statuses: Future[List[CiriumFlightStatus]] =
                (ciriumFlightStatusActor ? GetStatuses).mapTo[List[CiriumFlightStatus]]
              complete(statuses)
            })
        })
    }
}
