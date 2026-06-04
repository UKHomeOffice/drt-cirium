package uk.gov.homeoffice.cirium

import github.gphat.censorinus.StatsDClient

/** Minimal metrics abstraction used by feed and health components. */
trait MetricsCollector {

  def errorCounterMetric(name: String, value: Double = 1)

  def infoCounterMetric(name: String, value: Double = 1)

}

/** StatsD-backed implementation of [[MetricsCollector]]. */
case class MetricsCollectorService(statsd: StatsDClient) extends MetricsCollector {

  def counterMetric(name: String, value: Double): Unit = {
    statsd.counter(name, value)
  }

  def errorCounterMetric(name: String, value: Double = 1) = {
    counterMetric(s"error-$name", value)
  }

  def infoCounterMetric(name: String, value: Double = 1) = {
    counterMetric(s"info-$name", value)
  }
}
