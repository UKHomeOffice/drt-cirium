package uk.gov.homeoffice.cirium.services.feed

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, Uri }
import uk.gov.homeoffice.cirium.MetricsCollector

import scala.concurrent.{ ExecutionContext, Future }

class SkyCiriumClient(token: String, baseUrl: String, metricsCollector: MetricsCollector)(implicit
    system: ActorSystem,
    executionContext: ExecutionContext
) extends Cirium.Client(metricsCollector) {
  require(token.trim.nonEmpty, "CIRIUM_SKY_API_TOKEN must be configured when CIRIUM_USE_SKY_API=true")

  private val baseUri = Uri(baseUrl.stripSuffix("/"))
  require(
    baseUri.isAbsolute && Set("http", "https").contains(baseUri.scheme.toLowerCase),
    "CIRIUM_SKY_API_BASE_URL must be an absolute HTTP(S) URL"
  )

  private val skyOrigin = baseUri.copy(path = Uri.Path.Empty, rawQueryString = None, fragment = None)
  private val feedPath = "/v1/flights/status/feed"

  override protected val latestFeedEndpoint: String = endpoint(s"$feedPath/latest")

  override protected def previousFeedEndpoint(item: String, batchSize: Int): String =
    s"${feedItemEndpoint(item)}/previous/$batchSize"

  override protected def nextFeedEndpoint(item: String, batchSize: Int): String =
    s"${feedItemEndpoint(item)}/next/$batchSize"

  override protected def feedItemEndpoint(item: String): String = {
    val itemUri = Uri(item)
    if (itemUri.isAbsolute) validateSkyOrigin(itemUri).toString()
    else {
      val normalizedItem = item.stripPrefix("/")
      if (normalizedItem.startsWith("v1/")) endpoint(s"/$normalizedItem")
      else endpoint(s"$feedPath/$normalizedItem")
    }
  }

  override protected def request(endpoint: String): HttpRequest =
    HttpRequest(
      HttpMethods.GET,
      validateSkyOrigin(Uri(endpoint)),
      List(RawHeader("Accept", "application/json"), RawHeader("Authorization", token))
    )

  override protected val requestFailureMetric: String = "skyApiRequestFailure"

  override def sendReceive(request: HttpRequest): Future[HttpResponse] = Http().singleRequest(request)

  private def endpoint(path: String): String = skyOrigin.withPath(Uri.Path(path)).toString()

  private def validateSkyOrigin(uri: Uri): Uri = {
    val isSameOrigin = uri.isAbsolute &&
      uri.scheme.equalsIgnoreCase(skyOrigin.scheme) &&
      uri.authority.host.address.equalsIgnoreCase(skyOrigin.authority.host.address) &&
      effectivePort(uri) == effectivePort(skyOrigin)

    require(isSameOrigin, "Refusing to send Sky API credentials outside the configured Sky API origin")
    uri
  }

  private def effectivePort(uri: Uri): Int =
    if (uri.authority.port != 0) uri.authority.port
    else if (uri.scheme.equalsIgnoreCase("https")) 443
    else if (uri.scheme.equalsIgnoreCase("http")) 80
    else 0
}
