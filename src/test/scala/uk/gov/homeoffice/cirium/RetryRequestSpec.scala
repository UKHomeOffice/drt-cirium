package uk.gov.homeoffice.cirium

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import uk.gov.homeoffice.cirium.services.feed.Retry

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class RetryRequestSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty()))
  with SpecificationLike
  with AfterAll {

  implicit val s: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  private val fibDelays: Stream[FiniteDuration] = Retry.fibonacci(1).map(_.seconds)

  "Given a future, it should be executed twice on a first failure" >> {
    val probe = TestProbe("retryprobe")
    Retry.retry(Future {
      probe.ref ! "executed"
      throw new Exception("damn")
    }, Seq(1.millis, 1.millis), Option(2), 1.millis)
    probe.expectMsg(1.second, "executed")
    probe.expectMsg(1.second, "executed")
    success
  }

  "Given a function that returns a future, if it succeeds, should get the result" >> {

    def delayedResult:  Future[String] = Future("Yes")

    val futureResult = Retry.retry(delayedResult, fibDelays, Option(0), 1.second)
    val result = Await.result(futureResult, 1.second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails and retries are 0 we should get the failure" >> {

    def delayedResult = Future {
      throw new Exception("Failed")
      "Yes"
    }

    val futureResult = Retry.retry(delayedResult, fibDelays, Option(0), 1.second)

    Await.result(futureResult, 1.second) must throwA[Exception]
  }

  "Given a function that returns a future, if it fails first and then succeeds, we should get the success" >> {

    var calledTimes = 0

    def delayedResult: Future[String] = Future {
      if (calledTimes == 0) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult, fibDelays, Option(1), 1.second)
    val result = Await.result(futureResult, 5.second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails twice and then succeeds, we should get the success" >> {
    var calledTimes = 0

    def delayedResult = Future {
      if (calledTimes < 2) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult, fibDelays, Option(3), 1.second)
    val result = Await.result(futureResult, 5.second)

    result === "Yes"
  }

  "Given a function that returns a future, if it fails more times than the retry count we should get a failure" >> {

    var calledTimes = 0

    def delayedResult = Future {
      if (calledTimes < 3) {
        calledTimes += 1
        throw new Exception("Failed")
      } else
        "Yes"
    }

    val futureResult = Retry.retry(delayedResult, fibDelays, Option(1), 1.second)

    Await.result(futureResult, 1.second) must throwA[Exception]
  }

}

