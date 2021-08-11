package uk.gov.homeoffice.cirium.services.api

import akka.actor.{ ActorSystem, Scheduler }
import akka.stream.Materializer
import akka.util.Timeout
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait CiriumBaseRoutes {

  implicit def executionContext: ExecutionContext

  implicit def system: ActorSystem

  implicit def mat: Materializer

  implicit def scheduler: Scheduler

  implicit lazy val timeout: Timeout = 3.seconds

  def client: Cirium.Client

}
