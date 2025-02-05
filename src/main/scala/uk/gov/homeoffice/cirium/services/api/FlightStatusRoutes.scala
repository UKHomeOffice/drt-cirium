package uk.gov.homeoffice.cirium.services.api

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.server.Directives.{ concat, path, pathEnd, pathPrefix, rejectEmptyResponse, _ }
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.directives.MethodDirectives.get
import org.apache.pekko.http.scaladsl.server.directives.RouteDirectives.complete
import org.apache.pekko.pattern.{ AskableActorRef, ask }
import uk.gov.homeoffice.cirium.{ AppConfig, JsonSupport }
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
              complete(Map("Available ports" -> AppConfig.portCodes))
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
