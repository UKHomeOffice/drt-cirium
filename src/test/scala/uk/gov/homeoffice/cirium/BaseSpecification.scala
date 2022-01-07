package uk.gov.homeoffice.cirium
import github.gphat.censorinus.StatsDClient
import org.specs2.mutable.Specification

trait BaseSpecification extends Specification {
  val statsDClient: StatsDClient = new StatsDClient(hostname = AppConfig.statsdHost, port = AppConfig.statsdPort, prefix = AppConfig.statsdPrefix)

  val metricsService = MetricsService(statsDClient)
}
