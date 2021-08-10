package uk.gov.homeoffice.cirium.services.api

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives.{ concat, path, pathEnd, pathPrefix, rejectEmptyResponse, _ }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{ AskableActorRef, ask }
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.{ AppEnvironment, JsonSupport }
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor.GetReadiness
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.GetStatuses
import uk.gov.homeoffice.cirium.services.entities.CiriumFlightStatus
import uk.gov.homeoffice.cirium.services.feed.Retry
import uk.gov.homeoffice.cirium.services.health.{ AppHealthCheck, CiriumAppHealthSummaryConstructor }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait StatusRoutes extends CiriumBaseRoutes {

  import JsonSupport._

  def portActors: Map[String, ActorRef]

  def flightStatusActor: ActorRef

  private val logger = LoggerFactory.getLogger(getClass)

  lazy val appStatusRoutes: Route =
    pathPrefix("app-details") {
      concat(
        pathEnd {
          get {
            val eventualSummary = Retry.retry(
              CiriumAppHealthSummaryConstructor(flightStatusActor, portActors),
              Retry.fibonacciDelay,
              3,
              1 second)
            complete(eventualSummary)
          }
        },
        path("is-healthy") {
          concat(
            get {
              val healthChecker = AppHealthCheck(
                AppEnvironment.cirium_message_latency_tolerance seconds,
                AppEnvironment.cirium_lost_connect_tolerance seconds,
                client)

              complete(CiriumAppHealthSummaryConstructor(flightStatusActor, portActors).flatMap { hs =>
                healthChecker.isHealthy(hs).map { isHealthy: Boolean =>
                  if (isHealthy)
                    HttpResponse(StatusCodes.NoContent)
                  else
                    HttpResponse(StatusCodes.BadGateway)
                }.recover {
                  case exception: Exception =>
                    logger.error("Unable to check health data", exception)
                    HttpResponse(StatusCodes.BadGateway)
                }
              })
            })
        },
        path("is-ready") {
          get {
            val askableFlightStatusActor: AskableActorRef = flightStatusActor
            val response = (askableFlightStatusActor ? GetReadiness)
              .mapTo[Boolean].map { ready =>
                if (ready) {
                  logger.info(s"Ready to handle requests")
                  HttpResponse(StatusCodes.NoContent)
                } else {
                  logger.info(s"Not ready to handle requests")
                  HttpResponse(StatusCodes.BadGateway)
                }
              }
            complete(response)
          }
        },
        path(Segment) { portCode =>
          get {
            val maybeStatuses: Option[Future[List[CiriumFlightStatus]]] = portActors.get(portCode.toUpperCase).map {
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

}
