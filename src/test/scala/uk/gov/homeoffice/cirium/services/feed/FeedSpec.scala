package uk.gov.homeoffice.cirium.services.feed
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.testkit.TestProbe
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.homeoffice.cirium.services.feed.Cirium.Feed
import uk.gov.homeoffice.cirium.{MockBackwardsStrategy, MockMetricsCollector}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class MockClient(probe: ActorRef)(implicit ec: ExecutionContext, system: ActorSystem)
  extends Cirium.Client("appid", "appkey", "entrypoint", MockMetricsCollector) {

  override def sendReceive(uri: Uri): Future[HttpResponse] =  {
    probe ! uri.toString()
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
      val feed = Feed(client, 1.millisecond, MockBackwardsStrategy("some-url"))

      feed.start(1).flatMap(_.runWith(Sink.seq))

      val expectedInitialRequest = "entrypoint?appId=appid&appKey=appkey"

      probe.expectMsg(2.seconds, expectedInitialRequest)
      probe.expectMsg(5.seconds, expectedInitialRequest)
      probe.expectMsg(10.seconds, expectedInitialRequest)
    }
  }
}
