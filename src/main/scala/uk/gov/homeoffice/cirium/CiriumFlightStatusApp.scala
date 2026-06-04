package uk.gov.homeoffice.cirium

import org.apache.pekko.actor.{ActorRef, ActorSystem, Scheduler}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import github.gphat.censorinus.StatsDClient
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.AppConfig._
import uk.gov.homeoffice.cirium.actors.{CiriumFlightStatusRouterActor, CiriumPortStatusActor}
import uk.gov.homeoffice.cirium.services.api.{FlightScheduledRoutes, FlightStatusRoutes, StatusRoutes}
import uk.gov.homeoffice.cirium.services.feed.{BackwardsStrategyImpl, Cirium}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Application entrypoint for the Cirium adapter service.
 *
 * This object wires together the Cirium feed client, actor pipeline, and HTTP routes:
 *  - creates one [[uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor]] per configured port
 *  - creates a router actor to dispatch feed updates to those port actors
 *  - starts the Cirium feed stream and sends updates to the router actor
 *  - exposes status and scheduled-flight routes on port 8080
 */
object CiriumFlightStatusApp extends App with FlightStatusRoutes with StatusRoutes with FlightScheduledRoutes {
  private val log = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  val statsDClient: StatsDClient = new StatsDClient(hostname = AppConfig.statsdHost, port = AppConfig.statsdPort, prefix = AppConfig.statsdPrefix)

  val metricsCollector = MetricsCollectorService(statsDClient)

  /** Per-port in-memory stores keyed by port code. */
  val portActors: Map[String, ActorRef] = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(flightRetentionHours),
      s"$port-status-actor")).toMap

  /** Router actor that forwards each feed event to the correct port actor. */
  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val client: Cirium.ProdClient = new Cirium.ProdClient(
    ciriumAppId,
    ciriumAppKey,
    ciriumAppEntryPoint,
    metricsCollector)

  val targetTime = new DateTime().minus(AppConfig.goBackHours.hours.toMillis)

  /** Polling feed built from the Cirium client and backlog strategy. */
  val feed = Cirium.Feed(client, pollInterval, BackwardsStrategyImpl(client, targetTime, metricsCollector))

  val stepSize = 1000

  /**
   * Start consuming the feed and push each status message into the actor pipeline.
   * Stream completion/failures are surfaced to logs.
   */
  feed
    .start(step = stepSize)
    .map(_.runWith(Sink.actorRef(flightStatusActor, "complete", t => log.error("Failure", t))))

  /** Combined API surface for status, health, and scheduled-flight lookups. */
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

