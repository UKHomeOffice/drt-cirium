package uk.gov.homeoffice.cirium

import github.gphat.censorinus.StatsDClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class MetricsService(statsd: StatsDClient) {

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
