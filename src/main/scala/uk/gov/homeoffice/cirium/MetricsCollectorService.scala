package uk.gov.homeoffice.cirium

import github.gphat.censorinus.StatsDClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MetricsCollector {

  def errorCounterMetric(name: String, value: Double = 1)

  def infoCounterMetric(name: String, value: Double = 1)

}

case class MetricsCollectorService(statsd: StatsDClient) extends MetricsCollector {

  def counterMetric(name: String, value: Double): Unit = {
    Future(statsd.counter(name, value))
  }

  def errorCounterMetric(name: String, value: Double = 1) = {
    counterMetric(s"error-$name", value)
  }

  def infoCounterMetric(name: String, value: Double = 1) = {
    counterMetric(s"info-$name", value)
  }
}
