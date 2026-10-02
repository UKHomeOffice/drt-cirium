package uk.gov.homeoffice.cirium

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object AppConfig {

  private val config = ConfigFactory.load()

  val goBackHours: Int = config.getInt("drt-cirium.go-back-hours")

  val portCodes: Array[String] = config.getString("drt-cirium.port-codes").split(",")

  val pollInterval: FiniteDuration = config.getInt("drt-cirium.poll-interval-millis").millis

  val flightRetentionHours: Int = config.getInt("drt-cirium.flight-retention-hours")

  val ciriumMessageLatencyToleranceSeconds: Int = config.getInt("drt-cirium.message-latency-tolerance-seconds")

  val ciriumLostConnectToleranceSeconds: Int = config.getInt("drt-cirium.lost-connection-tolerance-seconds")

  val ciriumAppId: String = config.getString("cirium-feed.id")

  val ciriumAppKey: String = config.getString("cirium-feed.key")

  val ciriumAppEntryPoint: String = config.getString("cirium-feed.entry-point")

  val ciriumUseSkyApi: Boolean = config.getBoolean("cirium-feed.use-sky-api")

  val ciriumSkyApiToken: String = config.getString("cirium-feed.sky-api-token")

  val ciriumSkyApiBaseUrl: String = config.getString("cirium-feed.sky-api-base-url")

  def validateCiriumFeedConfig(): Unit =
    if (ciriumUseSkyApi)
      require(ciriumSkyApiToken.trim.nonEmpty, "CIRIUM_SKY_API_TOKEN must be configured when CIRIUM_USE_SKY_API=true")
    else {
      require(ciriumAppId.trim.nonEmpty, "CIRIUM_APP_ID must be configured when CIRIUM_USE_SKY_API=false")
      require(ciriumAppKey.trim.nonEmpty, "CIRIUM_APP_KEY must be configured when CIRIUM_USE_SKY_API=false")
    }

  val statsdHost: String = config.getString("statsd.host")

  val statsdPort: Int = config.getInt("statsd.port")

  val statsdPrefix: String = config.getString("statsd.prefix")

}
