package uk.gov.homeoffice.cirium.services.feed

import akka.actor.Scheduler
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import akka.pattern.after

object Retry {
  val log = Logger(getClass)
  val fibonacciDelay: Stream[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacciDelay zip fibonacciDelay.tail)
    .map { t => t._1 + t._2 }

  def retry[T](
    futureToRetry: => Future[T],
    delay: Seq[FiniteDuration],
    retries: Int, defaultDelay: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = futureToRetry
    .recoverWith {
      case _ if retries > 0 =>
        val nextDelayDuration = delay.headOption.getOrElse(defaultDelay)
        log.warn(s"Future failed. Trying again after $nextDelayDuration. $retries retries remaining")
        after(nextDelayDuration, s)(retry(futureToRetry, delay.tail, retries - 1, defaultDelay))
    }
}
