package uk.gov.homeoffice.cirium.services.health

import akka.actor.ActorRef
import akka.pattern.AskableActorRef
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import org.joda.time.DateTime
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor.GetHealth
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.GetPortFeedHealthSummary
import uk.gov.homeoffice.cirium.actors.{CiriumFeedHealthStatus, PortFeedHealthSummary}
import uk.gov.homeoffice.cirium.services.entities.CiriumMessageFormat
import uk.gov.homeoffice.cirium.services.feed.CiriumClientLike

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class CiriumAppHealthSummary(
                                   feedHealth: CiriumFeedHealthStatus,
                                   portFeedHealthSummaries: Map[String, PortFeedHealthSummary])

object CiriumAppHealthSummaryConstructor {
  implicit lazy val timeout: Timeout = 3.seconds

  def apply(
             flightStatusActor: ActorRef,
             portActors: Map[String, ActorRef])(implicit executionContext: ExecutionContext): Future[CiriumAppHealthSummary] = {
    val askableFlightStatusActor: AskableActorRef = flightStatusActor
    val eventualHealthStatus: Future[CiriumFeedHealthStatus] = (askableFlightStatusActor ? GetHealth)
      .mapTo[CiriumFeedHealthStatus]
    val eventualPortSummaries: Future[Map[String, PortFeedHealthSummary]] = Future.sequence(portActors.map {
      case (portCode, portActor) =>
        val askableActorRef: AskableActorRef = portActor
        (askableActorRef.ask(GetPortFeedHealthSummary)(Timeout(1 second)))
          .mapTo[PortFeedHealthSummary].map(portCode -> _)
    }).map(_.toMap)

    val futureAppHealth = for {
      healthStatus <- eventualHealthStatus
      portSummaries <- eventualPortSummaries

    } yield (CiriumAppHealthSummary(healthStatus, portSummaries))
    futureAppHealth
  }
}

case class AppHealthCheck(
                           acceptableMessageLatency: FiniteDuration,
                           acceptableLostConnectivityDuration: FiniteDuration,
                           ciriumClient: CiriumClientLike, now: () => Long = () => System.currentTimeMillis)(implicit context: ExecutionContext) {
  val log = Logger(getClass)

  def isHealthy(appHealthSummary: CiriumAppHealthSummary): Future[Boolean] = {
    val maybeLastProcessedMessageDateTime: Option[Long] = appHealthSummary
      .feedHealth
      .lastMessage
      .flatMap(_.messageIssuedAt)

    latestMessageDateTime(appHealthSummary).map(maybeLatestMessageTime => {

      val appIsStillCatchingUp = !appHealthSummary.feedHealth.isReady

      appIsStillCatchingUp || isHealthy(appHealthSummary, maybeLastProcessedMessageDateTime, maybeLatestMessageTime)
    })
  }

  def isHealthy(
                 appHealthSummary: CiriumAppHealthSummary,
                 maybeLastProcessedMessageDateTime: Option[Long],
                 maybeLatestMessageTime: Option[DateTime]
               ) = {
    (maybeLatestMessageTime, maybeLastProcessedMessageDateTime) match {
      case (Some(latestAvailableMessage), Some(latestProcessedMessage)) =>
        val latency = latestAvailableMessage.getMillis - latestProcessedMessage
        if (latency < acceptableMessageLatency.toMillis) {
          log.info(s"Current cirium latency ${latency / 1000} seconds - within allowable threshold")
          true
        } else {
          log.error(s"Current cirium latency ${latency / 1000} seconds - outside allowable threshold")
          false
        }
      case (None, Some(latestProcessedMessage)) =>
        val millisSinceContact = now() - latestProcessedMessage
        if (millisSinceContact < acceptableLostConnectivityDuration.toMillis) {
          log.warn(
            s"Cirium has been unresponsive for ${millisSinceContact / 1000} seconds - within allowable threshold")
          true
        } else {
          log.error(
            s"Cirium has been unresponsive for ${millisSinceContact / 1000} seconds - outside allowable threshold")
          false
        }
      case _ =>
        log.error(s"No messages processed. App Ready: ${appHealthSummary.feedHealth.isReady}")
        false
    }
  }

  def latestMessageDateTime(appHealthSummary: CiriumAppHealthSummary): Future[Option[DateTime]] = ciriumClient
    .initialRequest()
    .map(res => {
      CiriumMessageFormat.dateFromUri(res.item).toOption
    })
    .recover {
      case e: Throwable =>
        log.error("Failed to connect to cirium", e)
        None
    }
}
