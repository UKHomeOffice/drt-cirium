package uk.gov.homeoffice.cirium

import org.joda.time.DateTime
import org.specs2.mutable.Specification
import uk.gov.homeoffice.cirium.actors.CiriumFeedHealthStatus
import uk.gov.homeoffice.cirium.services.entities._
import uk.gov.homeoffice.cirium.services.health.{AppHealthCheck, CiriumAppHealthSummary}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class CiriumHealthSpec extends Specification {

  val ciriumStatus: CiriumFlightStatus = CiriumFlightStatus(
    100000,
    "",
    "",
    "",
    "",
    "",
    "",
    CiriumDate("2019-07-15T09:10:00.000Z", None),
    CiriumDate("2019-07-15T11:05:00.000Z", None),
    "",
    CiriumOperationalTimes(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None),
    None,
    None,
    List(),
    None,
    Seq())

  "When comparing the latest message received by our App to the latest one on the Cirium Feed" >> {

    "Given a latest processed message is the same as the latest on Cirium " +
      "Then the app is healthy" >> {
      val nextItemUri = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithInitialResponseOnly(nextItemUri)

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector)

      val healthResult = healthSummaryWithLatestMessageUri(nextItemUri)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === true
    }

    "Given a latest processed newer than the latest on Cirium " +
      "Then the app is healthy" >> {
      val currentLatestUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 29)
      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithInitialResponseOnly(currentLatestUrl)

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector)

      val healthResult = healthSummaryWithLatestMessageUri(lastProcessedUrl)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === true
    }

    "Given a last processed message that is older than the latest but within the threshold " +
      "Then the app is healthy" >> {
      val currentLatestUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 34)
      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithInitialResponseOnly(currentLatestUrl)

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector)

      val healthResult = healthSummaryWithLatestMessageUri(lastProcessedUrl)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === true
    }

    "Given a last processed message that is older and outside the threshold then the app is not healthy" >> {
      val currentLatestUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 36)
      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithInitialResponseOnly(currentLatestUrl)

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector)

      val healthResult = healthSummaryWithLatestMessageUri(lastProcessedUrl)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === false
    }

    "Given a last processed message that is within the connectivity threshold and a failed connection to Cirium " +
      "then the app is healthy" >> {

      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithFailure("")

      val now = () => new DateTime(2020, 2, 14, 14, 35)
        .getMillis

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector, now)

      val healthResult = healthSummaryWithLatestMessageUri(lastProcessedUrl)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === true
    }

    "Given a last processed message that is outside the connectivity threshold and a failed connection to Cirium " +
      "then the app is not healthy" >> {

      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithFailure("")

      val now = () => new DateTime(2020, 2, 14, 14, 41)
        .getMillis

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector, now)

      val healthResult = healthSummaryWithLatestMessageUri(lastProcessedUrl)

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === false
    }

    "Given the app is not yet ready, it should be regarded as healthy" >> {

      val lastProcessedUrl = urlForMessageAtDateTime(2020, 2, 14, 14, 30)
      val mockClientWithInitialResponseOnly = MockClientWithFailure("")

      val now = () => new DateTime(2020, 2, 14, 14, 41)
        .getMillis

      val healthChecker = AppHealthCheck(5.minutes, 10.minutes, mockClientWithInitialResponseOnly, MockMetricsCollector, now)

      val healthResult = CiriumAppHealthSummary(
        CiriumFeedHealthStatus(isReady = false, None, 0L), Map())

      val result: Boolean = Await.result(healthChecker.isHealthy(healthResult), 1.second)

      result === true
    }
  }

  def healthSummaryWithLatestMessageUri(nextItemUri: String): CiriumAppHealthSummary = {
    CiriumAppHealthSummary(CiriumFeedHealthStatus(true, Option(CiriumTrackableStatus(ciriumStatus, nextItemUri, 0L)), 0L), Map())
  }

  def urlForMessageAtDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): String = {
    f"https://something.something.endpoint/rest/v2/json/$year/$month%02d/$day%02d/$hour%02d/$minute%02d/00/00/hash"
  }
}
