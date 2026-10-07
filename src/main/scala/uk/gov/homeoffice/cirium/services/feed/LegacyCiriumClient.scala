package uk.gov.homeoffice.cirium.services.feed

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import uk.gov.homeoffice.cirium.MetricsCollector

import scala.concurrent.{ ExecutionContext, Future }

class LegacyCiriumClient(appId: String, appKey: String, entryPoint: String, metricsCollector: MetricsCollector)(implicit
    system: ActorSystem,
    executionContext: ExecutionContext
) extends Cirium.Client(metricsCollector) {

  override protected val latestFeedEndpoint: String = entryPoint

  override protected def previousFeedEndpoint(item: String, batchSize: Int): String = s"$item/previous/$batchSize"

  override protected def nextFeedEndpoint(item: String, batchSize: Int): String = s"$item/next/$batchSize"

  override protected def feedItemEndpoint(item: String): String = item

  override protected def request(endpoint: String): HttpRequest = {
    val uri = Uri(endpoint).withRawQueryString(s"appId=$appId&appKey=$appKey")
    HttpRequest(HttpMethods.GET, uri)
  }

  override protected val requestFailureMetric: String = "ciriumRequestFailure"

  override def sendReceive(request: HttpRequest): Future[HttpResponse] = Http().singleRequest(request)
}
