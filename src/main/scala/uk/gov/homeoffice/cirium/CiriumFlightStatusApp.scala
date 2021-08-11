package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.Sink
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.AppEnvironment._
import uk.gov.homeoffice.cirium.actors.{ CiriumFlightStatusRouterActor, CiriumPortStatusActor }
import uk.gov.homeoffice.cirium.services.api.{ FlightScheduledRoutes, FlightStatusRoutes, StatusRoutes }
import uk.gov.homeoffice.cirium.services.feed.{ BackwardsStrategyImpl, Cirium }

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object CiriumFlightStatusApp extends App with FlightStatusRoutes with StatusRoutes with FlightScheduledRoutes {
  private val log = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  val portActors: Map[String, ActorRef] = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(statusRetentionDurationHours),
      s"$port-status-actor")).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val client: Cirium.ProdClient = new Cirium.ProdClient(
    cirium_app_Id,
    cirium_app_key,
    cirium_app_entry_point)

  val millis = 48.hours.toMillis
  val targetTime = new DateTime().minus(millis)

  val feed = Cirium.Feed(client, pollEveryMillis = pollMillis, BackwardsStrategyImpl(client, targetTime))

  val stepSize = 1000

  feed.start(step = stepSize).map(source => {
    source.runWith(Sink.actorRef(flightStatusActor, "complete", t => log.error("Failure", t)))
  })

  lazy val routes: Route = flightStatusRoutes ~ flightTrackableStatusRoutes ~ appStatusRoutes ~ flightScheduledRoute

  val serverBinding: Future[Http.ServerBinding] = Http().newServerAt("0.0.0.0", 8080).bind(routes)

  serverBinding.onComplete {
    case Success(bound) =>
      log.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      log.error(s"Server could not start!", e)
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

