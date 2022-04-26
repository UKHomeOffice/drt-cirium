package uk.gov.homeoffice.cirium

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}

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

   val statsdHost: String = config.getString("statsd.host")

   val statsdPort: Int = config.getInt("statsd.port")

   val statsdPrefix: String = config.getString("statsd.prefix")

}
