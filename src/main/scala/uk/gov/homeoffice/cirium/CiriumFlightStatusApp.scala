package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{ AskableActorRef, ask }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor.{ GetAllFlightDeltas, GetFlightDeltas }
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.{ GetStatuses, RemoveExpired }
import uk.gov.homeoffice.cirium.actors.{ CiriumFlightStatusRouterActor, CiriumPortStatusActor }
import uk.gov.homeoffice.cirium.services.entities.CiriumFlightStatus
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.collection.mutable
import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object CiriumFlightStatusApp extends App {

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val timeout: Timeout = 3.seconds

  lazy val routes: Route = flightStatusRoutes ~ flightStatusDeltasRoutes

  val portCodes = sys.env("PORT_CODES").split(",")

  val portActors = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(sys.env("CIRIUM_STATUS_RETENTION_DURATION").toInt),
      s"$port-status-actor")).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "0.0.0.0", 8080)

  system
    .scheduler
    .schedule(1 minute, 1 minute)(() => portActors.mapValues(actor => actor ! RemoveExpired))

  val client = new Cirium.ProdClient(
    sys.env("CIRIUM_APP_ID"),
    sys.env("CIRIUM_APP_KEY"),
    sys.env("CIRIUM_APP_ENTRY_POINT"))

  val feed = Cirium.Feed(client)

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

  lazy val flightStatusDeltasRoutes: Route =
    pathPrefix("deltas") {
      concat(
        pathEnd {
          concat(
            get {
              rejectEmptyResponse {
                complete {
                  val askableRouterActor: AskableActorRef = flightStatusActor
                  (askableRouterActor ? GetAllFlightDeltas)
                    .mapTo[mutable.Map[Int, mutable.Seq[CiriumFlightStatus]]].map {
                      statuses =>
                        statuses.map {
                          case (flightId, updates) => flightId.toString -> updates.size
                        }.toMap
                    }
                }
              }
            })
        },
        path(Segment) { flightId =>
          get {
            val askableRouterActor: AskableActorRef = flightStatusActor
            rejectEmptyResponse {
              complete {
                (askableRouterActor ? GetFlightDeltas(flightId.toInt))
                  .mapTo[List[CiriumFlightStatus]]
              }
            }
          }
        })
    }

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${
        bound.localAddress.getHostString
      }:${
        bound.localAddress.getPort
      }/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

