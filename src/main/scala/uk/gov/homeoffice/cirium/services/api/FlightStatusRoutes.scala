package uk.gov.homeoffice.cirium.services.api

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives.{ concat, path, pathEnd, pathPrefix, rejectEmptyResponse, _ }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{ AskableActorRef, ask }
import uk.gov.homeoffice.cirium.{ AppEnvironment, JsonSupport }
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.{ GetStatuses, GetTrackableStatuses }
import uk.gov.homeoffice.cirium.services.entities.{ CiriumFlightStatus, CiriumTrackableStatus }

import scala.language.postfixOps

trait FlightStatusRoutes extends CiriumBaseRoutes {

  def portActors: Map[String, ActorRef]

  import JsonSupport._

  lazy val flightStatusRoutes: Route =
    pathPrefix("statuses") {
      concat(
        pathEnd {
          concat(
            get {
              complete(Map("Available ports" -> AppEnvironment.portCodes))
            })
        },
        path(Segment) { portCode =>
          get {
            val maybeStatuses = portActors.get(portCode.toUpperCase).map {
              actor =>
                val askablePortActor: AskableActorRef = actor
                (askablePortActor ? GetStatuses)
                  .mapTo[List[CiriumFlightStatus]]
            }
            rejectEmptyResponse {
              complete(maybeStatuses)
            }
          }
        })
    }

  lazy val flightTrackableStatusRoutes: Route =
    pathPrefix("statuses-tracked") {
      concat(
        path(Segment) { portCode =>
          get {
            val maybeStatuses = portActors.get(portCode.toUpperCase).map {
              actor =>
                val askablePortActor: AskableActorRef = actor
                (askablePortActor ? GetTrackableStatuses)
                  .mapTo[List[CiriumTrackableStatus]]
            }
            rejectEmptyResponse {
              complete(maybeStatuses)
            }
          }
        })
    }

}
