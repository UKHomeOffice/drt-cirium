package uk.gov.homeoffice.cirium.services.feed

import akka.actor.ActorSystem
import akka.pattern.after
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

object Retry {
  private val log = LoggerFactory.getLogger(getClass)
  val fibonacciDelay: Stream[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacciDelay zip fibonacciDelay.tail).map { t => t._1 + t._2 }

  def retry[T](futureToRetry: => Future[T], delay: Seq[FiniteDuration], retries: Int, defaultDelay: FiniteDuration)(implicit actorSystem: ActorSystem, ec: ExecutionContext): Future[T] =
    futureToRetry.recoverWith {
      case e if retries > 0 =>
        val nextDelayDuration = delay.headOption.getOrElse(defaultDelay)
        log.warn(s"Future failed. Trying again after $nextDelayDuration. $retries retries remaining", e)
        after(nextDelayDuration)(retry(futureToRetry, delay.tail, retries - 1, defaultDelay))
    }
}
