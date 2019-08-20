package gov.uk.homeoffice.drt

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import gov.uk.homeoffice.drt.services.feed.Cirium
import gov.uk.homeoffice.drt.services.feed.Cirium.{ Feed, ProdClient }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }

object CiriumFlightStatusApp extends App with FlightStatusRoutes {

  implicit val system: ActorSystem = ActorSystem("cirium-flight-status-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val flightStatusActor: ActorRef = system.actorOf(CiriumFlightStatusActor.props, "flight-status-actor")

  lazy val routes: Route = userRoutes

  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)

  val client = new Cirium.ProdClient(
    sys.env("CIRIUM_APP_ID"),
    sys.env("CIRIUM_APP_KEY"),
    sys.env("CIRIUM_APP_ENTRY_POINT"))
  val feed = Cirium.Feed(client)

  feed.start().map(source => {
    source.runWith(Sink.actorRef(flightStatusActor, "complete"))
  })

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }
  Await.result(system.whenTerminated, Duration.Inf)
}

