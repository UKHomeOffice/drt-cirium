package uk.gov.homeoffice.cirium

import akka.actor.{ ActorSystem, Scheduler }
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterEach
import uk.gov.homeoffice.cirium.services.feed.Retry

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

class RetryRequestSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty()))
  with SpecificationLike
  with AfterEach {

  implicit val s: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def after: Unit = TestKit.shutdownActorSystem(system)

  "Given a function that returns a future, if it succeeds, should get the result" >> {

    def delayedResult = () => Future("Yes")

    val futureResult = Retry.retry(delayedResult(), Retry.fibonacciDelay, 0, 1 second)
    val result = Await.result(futureResult, 1 second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails and retries are 0 we should get the failure" >> {

    def delayedResult = () => Future {
      throw new Exception("Failed")
      "Yes"
    }

    val futureResult = Retry.retry(delayedResult(), Retry.fibonacciDelay, 0, 1 second)

    Await.result(futureResult, 1 second) must throwA[Exception]
  }

  "Given a function that returns a future, if it fails first and then succeeds, we should get the success" >> {

    var calledTimes = 0

    def delayedResult = () => Future {
      if (calledTimes == 0) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult(), Retry.fibonacciDelay, 1, 1 second)
    val result = Await.result(futureResult, 5 second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails twice and then succeeds, we should get the success" >> {

    var calledTimes = 0

    def delayedResult = () => Future {
      if (calledTimes < 2) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult(), Retry.fibonacciDelay, 3, 1 second)
    val result = Await.result(futureResult, 5 second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails more times than the retry count we should get a failure" >> {

    var calledTimes = 0

    def delayedResult = () => Future {
      if (calledTimes < 3) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult(), Retry.fibonacciDelay, 1, 1 second)

    Await.result(futureResult, 1 second) must throwA[Exception]
  }

}

