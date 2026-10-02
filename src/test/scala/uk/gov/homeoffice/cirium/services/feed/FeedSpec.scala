package uk.gov.homeoffice.cirium.services.feed
import org.apache.pekko.actor.{ ActorRef, ActorSystem }
import org.apache.pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.testkit.TestProbe
import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.homeoffice.cirium.services.entities._
import uk.gov.homeoffice.cirium.services.feed.Cirium.Feed
import uk.gov.homeoffice.cirium.{ MetricsCollector, MockBackwardsStrategy, MockMetricsCollector }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

class MockClient(probe: ActorRef)(implicit ec: ExecutionContext, system: ActorSystem)
    extends LegacyCiriumClient("appid", "appkey", "entrypoint", MockMetricsCollector) {

  override def sendReceive(request: HttpRequest): Future[HttpResponse] = {
    probe ! request.uri.toString()
    Future.successful(HttpResponse(StatusCodes.BadGateway))
  }
}

class FeedSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("feedtest")
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Feed" should {
    "Not fail after repeated unsuccessful calls to the cirium endpoints" in {
      val probe = TestProbe("feedtest")
      val client = new MockClient(probe.ref)
      val feed = Feed(client, 1.millisecond, MockBackwardsStrategy("some-url"), MockMetricsCollector)

      feed.start(1).flatMap(_.runWith(Sink.seq))

      val expectedInitialRequest = "entrypoint?appId=appid&appKey=appkey"

      probe.expectMsg(2.seconds, expectedInitialRequest)
      probe.expectMsg(5.seconds, expectedInitialRequest)
      probe.expectMsg(10.seconds, expectedInitialRequest)
    }
  }
}

class SkyApiClientSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("sky-api-client-test")
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val itemId = "2026/09/29/12/30/00/123/skyItem"
  private val jsonResponse = HttpResponse(
    StatusCodes.OK,
    entity = HttpEntity(
      ContentTypes.`application/json`,
      s"""{"request":{"endpoint":"latest","url":"https://api.sky.cirium.com/v1/flights/status/feed/latest"},"item":"$itemId"}"""
    )
  )
  private val itemListResponse = HttpResponse(
    StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, s"""{"items":["$itemId"]}""")
  )

  private class CapturingLegacyClient(response: HttpResponse, metrics: MetricsCollector)
      extends LegacyCiriumClient("legacy-id", "legacy-key", "https://legacy.example/latest", metrics) {
    var requests: List[HttpRequest] = List.empty

    override def sendReceive(request: HttpRequest): Future[HttpResponse] = {
      requests = requests :+ request
      Future.successful(response)
    }
  }

  private class CapturingSkyClient(
      response: HttpResponse,
      metrics: MetricsCollector,
      token: String = "sky-token",
      baseUrl: String = "https://sky.example"
  ) extends SkyCiriumClient(token, baseUrl, metrics) {
    var requests: List[HttpRequest] = List.empty

    override def sendReceive(request: HttpRequest): Future[HttpResponse] = {
      requests = requests :+ request
      Future.successful(response)
    }
  }

  private class RecordingMetrics extends MetricsCollector {
    var errors: List[String] = List.empty

    override def errorCounterMetric(name: String, value: Double): Unit = errors = errors :+ name
    override def infoCounterMetric(name: String, value: Double): Unit = ()
  }

  private class BackwardsClient(items: List[String]) extends CiriumClientLike {
    private def unsupported[T]: Future[T] = Future.failed(new UnsupportedOperationException)

    override def initialRequest(): Future[CiriumInitialResponse] = unsupported
    override def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] =
      Future.successful(CiriumItemListResponse(items))
    override def forwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] = unsupported
    override def makeRequest(endpoint: String, maybeMaxRetries: Option[Int]): Future[HttpResponse] = unsupported
    override def sendReceive(request: HttpRequest): Future[HttpResponse] = unsupported
    override def fetchFlightStatus(endpoint: String): Future[CiriumFlightStatusResponse] = unsupported
  }

  override def afterAll(): Unit = system.terminate()

  "Cirium client" should {
    "preserve legacy query-string authentication by default" in {
      val client = new CapturingLegacyClient(jsonResponse, MockMetricsCollector)

      Await.result(client.initialRequest(), 1.second)

      client.requests.head.uri.toString() shouldBe "https://legacy.example/latest?appId=legacy-id&appKey=legacy-key"
      client.requests.head.headers shouldBe empty
    }

    "use Sky authentication headers and Sky feed paths without credentials in URLs" in {
      val client = new CapturingSkyClient(jsonResponse, MockMetricsCollector, baseUrl = "https://sky.example/")

      Await.ready(client.initialRequest(), 1.second)
      Await.ready(client.backwards(itemId, 1000), 1.second)
      Await.ready(client.forwards(itemId), 1.second)
      Await.ready(client.fetchFlightStatus(itemId), 1.second)

      client.requests.map(_.uri.toString()) shouldBe List(
        "https://sky.example/v1/flights/status/feed/latest",
        s"https://sky.example/v1/flights/status/feed/$itemId/previous/1000",
        s"https://sky.example/v1/flights/status/feed/$itemId/next/1000",
        s"https://sky.example/v1/flights/status/feed/$itemId"
      )
      client.requests.foreach { request =>
        request.uri.query().toString() shouldBe ""
        request.headers.find(_.is("accept")).map(_.value()) shouldBe Some("application/json")
        request.headers.find(_.is("authorization")).map(_.value()) shouldBe Some("sky-token")
      }
    }

    "refuse to send a Sky token to an off-origin feed URL" in {
      val client = new CapturingSkyClient(jsonResponse, MockMetricsCollector)

      Await.result(client.fetchFlightStatus("https://untrusted.example/item").failed, 1.second)

      client.requests shouldBe empty
    }

    "accept the explicit default HTTPS port as the configured Sky origin" in {
      val client = new CapturingSkyClient(jsonResponse, MockMetricsCollector)

      Await.result(client.fetchFlightStatus("https://sky.example:443/v1/flights/status/feed/item"), 1.second)
      client.requests.head.uri.toString() shouldBe "https://sky.example/v1/flights/status/feed/item"
    }

    "reject an empty Sky token when the provider is selected" in {
      val error =
        intercept[IllegalArgumentException](new CapturingSkyClient(jsonResponse, MockMetricsCollector, token = ""))

      error.getMessage should include("CIRIUM_SKY_API_TOKEN")
    }

    "decode Sky latest, previous, and next response envelopes with the existing models" in {
      val latestClient = new CapturingSkyClient(jsonResponse, MockMetricsCollector)
      val itemListClient = new CapturingSkyClient(itemListResponse, MockMetricsCollector)

      Await.result(latestClient.initialRequest(), 1.second).item shouldBe itemId
      Await.result(itemListClient.backwards(itemId, 1000), 1.second).items shouldBe List(itemId)
      Await.result(itemListClient.forwards(itemId), 1.second).items shouldBe List(itemId)
    }

    "record a safe Sky request failure metric" in {
      val metrics = new RecordingMetrics
      val client = new CapturingSkyClient(HttpResponse(StatusCodes.BadGateway), metrics)

      val result = client.makeRequest("https://sky.example/v1/flights/status/feed/latest", Some(0))

      Await.result(result.failed, 1.second)
      metrics.errors should contain("skyApiRequestFailure-502")
      client.requests.head.uri.toString() should not include "sky-token"
    }
  }

  "BackwardsStrategyImpl" should {
    "derive a timestamp from a Sky feed item ID" in {
      val targetTime = new DateTime(2026, 9, 29, 12, 30, 0)
      val strategy = BackwardsStrategyImpl(new BackwardsClient(List(itemId)), targetTime, MockMetricsCollector)

      Await.result(strategy.backwardsFrom(itemId), 1.second) shouldBe itemId
      CiriumMessageFormat.dateFromUri(s"https://api.sky.cirium.com/v1/flights/status/feed/$itemId").toOption shouldBe
        Some(targetTime)
    }

    "fail clearly when Sky backfill returns no items" in {
      val strategy = BackwardsStrategyImpl(new BackwardsClient(Nil), DateTime.now, MockMetricsCollector)

      Await.result(strategy.backwardsFrom(itemId).failed, 1.second).getMessage shouldBe
        "Failed to backfill: Cirium returned no previous feed items"
    }
  }
}
