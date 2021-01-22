package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.Logger
import uk.gov.homeoffice.cirium.actors.{ CiriumFlightStatusRouterActor, CiriumPortStatusActor }
import uk.gov.homeoffice.cirium.services.api.{ FlightStatusRoutes, FlightScheduledRoutes, StatusRoutes }
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

trait Environment {

  val goBackHops = sys.env.getOrElse("CIRIUM_GO_BACK_X_1000", "5").toInt

  val portCodes: Array[String] = sys.env("PORT_CODES").split(",")

  val pollMillis = sys.env.getOrElse("CIRIUM_POLL_MILLIS", "5000").toInt

  val statusRetentionDurationHours = sys.env.getOrElse("CIRIUM_STATUS_RETENTION_DURATION_HOURS", "24").toInt

}

object CiriumFlightStatusApp extends App with FlightStatusRoutes with StatusRoutes with FlightScheduledRoutes with Environment {
  private val log = Logger(getClass)

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  val portActors: Map[String, ActorRef] = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(statusRetentionDurationHours),
      s"$port-status-actor")).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val client: Cirium.ProdClient = new Cirium.ProdClient(
    sys.env("CIRIUM_APP_ID"),
    sys.env("CIRIUM_APP_KEY"),
    sys.env("CIRIUM_APP_ENTRY_POINT"))

  val feed = Cirium.Feed(client, pollEveryMillis = pollMillis)

  feed.start(goBackHops).map(source => {
    source.runWith(Sink.actorRef(flightStatusActor, "complete"))
  })

  lazy val routes: Route = flightStatusRoutes ~ flightTrackableStatusRoutes ~ appStatusRoutes ~ flightScheduledRoute

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "0.0.0.0", 8080)

  serverBinding.onComplete {
    case Success(bound) =>
      log.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      log.error(s"Server could not start!", e)
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

