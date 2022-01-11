package uk.gov.homeoffice.cirium

import github.gphat.censorinus.StatsDClient

trait MetricsCollector {

  def errorCounterMetric(name: String, value: Double = 1)

  def infoCounterMetric(name: String, value: Double = 1)

}

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
