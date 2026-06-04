package uk.gov.homeoffice.cirium.services.feed

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/** Retry helpers with delayed re-execution support. */
object Retry {
  private val log = LoggerFactory.getLogger(getClass)
  /** Fibonacci-like delay sequence capped at `max`. */
  def fibonacci(max: Int): Stream[Int] = 0 #:: 1 #:: (fibonacci(max) zip fibonacci(max).tail).map { t =>
    (t._1 + t._2) match {
      case overMax if max < overMax => max
      case newDelay => newDelay
    }
  }

  /**
   * Retries a failing future according to provided delays.
   *
   * Retries stop when `maybeMaxRetries` reaches zero, or continue indefinitely when empty.
   */
  def retry[T](futureToRetry: => Future[T], delay: Seq[FiniteDuration], maybeMaxRetries: Option[Int], defaultDelay: FiniteDuration)(implicit actorSystem: ActorSystem, ec: ExecutionContext): Future[T] = {
    futureToRetry.recoverWith {
      case e if maybeMaxRetries.isEmpty || 0 < maybeMaxRetries.get =>
        val nextDelayDuration = delay.headOption.getOrElse(defaultDelay)
        log.warn(s"Future failed. Trying again after $nextDelayDuration. ${maybeMaxRetries.getOrElse("Unlimited")} retries remaining", e)
        after(nextDelayDuration)(retry(futureToRetry, delay.tail, maybeMaxRetries.map(_ - 1), defaultDelay))
    }
  }
}
