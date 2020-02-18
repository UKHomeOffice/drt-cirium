package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{ AskableActorRef, ask }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor.GetReadiness
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.{ GetStatuses, GetTrackableStatuses }
import uk.gov.homeoffice.cirium.actors.{ CiriumFlightStatusRouterActor, CiriumPortStatusActor }
import uk.gov.homeoffice.cirium.services.entities.{ CiriumFlightStatus, CiriumTrackableStatus }
import uk.gov.homeoffice.cirium.services.feed.Cirium
import uk.gov.homeoffice.cirium.services.health.{ AppHealthCheck, CiriumAppHealthSummaryConstructor }

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object CiriumFlightStatusApp extends App {
  val log = Logger(getClass)

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val timeout: Timeout = 3.seconds

  lazy val routes: Route = flightStatusRoutes ~ flightTrackableStatusRoutes ~ appStatusRoutes

  val portCodes = sys.env("PORT_CODES").split(",")

  val portActors = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(sys.env.getOrElse("CIRIUM_STATUS_RETENTION_DURATION_HOURS", "24").toInt),
      s"$port-status-actor")).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "0.0.0.0", 8080)

  val client = new Cirium.ProdClient(
    sys.env("CIRIUM_APP_ID"),
    sys.env("CIRIUM_APP_KEY"),
    sys.env("CIRIUM_APP_ENTRY_POINT"))

  val feed = Cirium.Feed(client, pollEveryMillis = sys.env.getOrElse("CIRIUM_POLL_MILLIS", "5000").toInt)

  val goBackHops = sys.env.getOrElse("CIRIUM_GO_BACK_X_1000", "5").toInt

  feed.start(goBackHops).map(source => {
    source.runWith(Sink.actorRef(flightStatusActor, "complete"))
  })

  import JsonSupport._

  lazy val flightStatusRoutes: Route =
    pathPrefix("statuses") {
      concat(
        pathEnd {
          concat(
            get {
              complete(Map("Available ports" -> portCodes))
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

  lazy val appStatusRoutes: Route =
    pathPrefix("app-details") {
      concat(
        pathEnd {
          get {
            complete(CiriumAppHealthSummaryConstructor(flightStatusActor, portActors))
          }
        },
        path("is-healthy") {
          concat(
            get {
              val healthChecker = AppHealthCheck(
                sys.env.getOrElse("CIRIUM_MESSAGE_LATENCY_TOLERANCE_SECONDS", "60").toInt seconds,
                sys.env.getOrElse("CIRIUM_LOST_CONNECTION_TOLERANCE_SECONDS", "300").toInt seconds,
                client)

              complete(CiriumAppHealthSummaryConstructor(flightStatusActor, portActors).flatMap { hs =>
                healthChecker.isHealthy(hs).map { isHealthy: Boolean =>
                  if (isHealthy)
                    HttpResponse(StatusCodes.NoContent)
                  else
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
                  log.info(s"Ready to handle requests")
                  HttpResponse(StatusCodes.NoContent)
                } else {
                  log.info(s"Not ready to handle requests")
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
  serverBinding.onComplete {
    case Success(bound) =>
      log.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      log.error(s"Server could not start!", e)
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

