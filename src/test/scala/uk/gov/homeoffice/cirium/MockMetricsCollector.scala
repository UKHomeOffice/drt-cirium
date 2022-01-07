package uk.gov.homeoffice.cirium

class MockMetricsCollector extends MetricsCollector {
  override def errorCounterMetric(name: String, value: Double): Unit = println(s"error-$name")

  override def infoCounterMetric(name: String, value: Double): Unit = println(s"info-$name")
}