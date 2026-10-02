package uk.gov.homeoffice.cirium

import org.apache.pekko.actor.{ ActorRef, ActorSystem, Scheduler }
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import github.gphat.censorinus.StatsDClient
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import uk.gov.homeoffice.cirium.AppConfig._
import uk.gov.homeoffice.cirium.actors.{ CiriumFlightStatusRouterActor, CiriumPortStatusActor }
import uk.gov.homeoffice.cirium.services.api.{ FlightStatusRoutes, StatusRoutes }
import uk.gov.homeoffice.cirium.services.feed.{ BackwardsStrategyImpl, Cirium, LegacyCiriumClient, SkyCiriumClient }

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object CiriumFlightStatusApp extends App with FlightStatusRoutes with StatusRoutes {
  private val log = LoggerFactory.getLogger(getClass)
  AppConfig.validateCiriumFeedConfig()

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  val statsDClient: StatsDClient =
    new StatsDClient(hostname = AppConfig.statsdHost, port = AppConfig.statsdPort, prefix = AppConfig.statsdPrefix)

  val metricsCollector = MetricsCollectorService(statsDClient)

  val portActors: Map[String, ActorRef] = portCodes.map(port =>
    port -> system.actorOf(
      CiriumPortStatusActor.props(flightRetentionHours),
      s"$port-status-actor"
    )
  ).toMap

  val flightStatusActor: ActorRef = system
    .actorOf(CiriumFlightStatusRouterActor.props(portActors), "flight-status-actor")

  val client: Cirium.Client =
    if (ciriumUseSkyApi) new SkyCiriumClient(ciriumSkyApiToken, ciriumSkyApiBaseUrl, metricsCollector)
    else new LegacyCiriumClient(ciriumAppId, ciriumAppKey, ciriumAppEntryPoint, metricsCollector)

  val targetTime = new DateTime().minus(AppConfig.goBackHours.hours.toMillis)

  val feed =
    Cirium.Feed(client, pollInterval, BackwardsStrategyImpl(client, targetTime, metricsCollector), metricsCollector)

  val stepSize = 1000

  feed
    .start(step = stepSize)
    .map(_.runWith(Sink.actorRef(
      flightStatusActor,
      "complete",
      t => log.error("[CiriumFlightStatusApp][feed] Failure", t)
    )))

  lazy val routes: Route = flightStatusRoutes ~ flightTrackableStatusRoutes ~ appStatusRoutes

  val serverBinding: Future[Http.ServerBinding] = Http().newServerAt("0.0.0.0", 8080).bind(routes)

  serverBinding.onComplete {
    case Success(bound) =>
      log.info(
        s"[CiriumFlightStatusApp][serverBinding] Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/"
      )
    case Failure(e) =>
      log.error(s"[CiriumFlightStatusApp][serverBinding] Server could not start", e)
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}
