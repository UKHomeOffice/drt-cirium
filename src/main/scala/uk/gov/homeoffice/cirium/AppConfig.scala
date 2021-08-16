package uk.gov.homeoffice.cirium

import com.typesafe.config.ConfigFactory

object AppConfig {

  private val config = ConfigFactory.load()

  val goBackHours: Int = config.getInt("drt-cirium.go-back-hours")

  val portCodes: Array[String] = config.getString("drt-cirium.port-codes").split(",")

  val pollIntervalMillis: Int = config.getInt("drt-cirium.poll-interval-millis")

  val flightRetentionHours: Int = config.getInt("drt-cirium.flight-retention-hours")

  val ciriumMessageLatencyToleranceSeconds: Int = config.getInt("drt-cirium.message-latency-tolerance-seconds")

  val ciriumLostConnectToleranceSeconds: Int = config.getInt("drt-cirium.lost-connection-tolerance-seconds")

  val ciriumAppId: String = config.getString("cirium-feed.id")

  val ciriumAppKey: String = config.getString("cirium-feed.key")

  val ciriumAppEntryPoint: String = config.getString("cirium-feed.entry-point")

}
