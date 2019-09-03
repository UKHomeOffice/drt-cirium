package gov.uk.homeoffice.drt

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.{AskableActorRef, ask}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import gov.uk.homeoffice.drt.actors.{CiriumFlightStatusRouterActor, CiriumPortStatusActor}
import gov.uk.homeoffice.drt.actors.CiriumFlightStatusRouterActor.GetStatuses
import gov.uk.homeoffice.drt.services.entities.CiriumFlightStatus
import gov.uk.homeoffice.drt.services.feed.Cirium

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object CiriumFlightStatusApp extends App {

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val timeout: Timeout = 3.seconds

  lazy val routes: Route = flightStatusRoutes

  val portCodes = sys.env("PORT_CODES").split(",")

  val portActors = portCodes.map(port =>
    port -> system.actorOf(CiriumPortStatusActor.props, s"$port-status-actor")).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)

  val client = new Cirium.ProdClient(
    sys.env("CIRIUM_APP_ID"),
    sys.env("CIRIUM_APP_KEY"),
    sys.env("CIRIUM_APP_ENTRY_POINT")
  )

  val feed = Cirium.Feed(client)

  feed.start(5).map(source => {
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

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

