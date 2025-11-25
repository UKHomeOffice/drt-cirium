package uk.gov.homeoffice.cirium

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.ask
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterEach
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor
import uk.gov.homeoffice.cirium.actors.CiriumPortStatusActor.{GetStatuses, RemoveExpired}
import uk.gov.homeoffice.cirium.services.entities._

import scala.concurrent.Await
import scala.concurrent.duration._

class CiriumPortActorSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty()))
  with SpecificationLike
  with AfterEach {
  sequential
  isolated

  override def after: Unit = TestKit.shutdownActorSystem(system)

  "Flight statuses be deleted after a given period" >> {
    val portStatusActor = system.actorOf(
      CiriumPortStatusActor.props(1, () => DateTime.parse("2019-09-04T11:51:00.000Z").getMillis),
      "test-status-actor")
    implicit lazy val timeout: Timeout = 3.seconds

    val statusToExpire = CiriumTrackableStatus(MockFlightStatus(1, "2019-09-04T10:50:59.000Z"), "", 0L)
    val statusToKeep = CiriumTrackableStatus(MockFlightStatus(2, "2019-09-04T11:51:01.000Z"), "", 0L)

    portStatusActor ! statusToExpire
    portStatusActor ! statusToKeep

    portStatusActor ! RemoveExpired

    val result = Await.result(portStatusActor ? GetStatuses, 1.second)
    val expected = List(statusToKeep.status)

    result === expected
  }
}

object MockFlightStatus {

  def apply(id: Int, scheduledDate: String): CiriumFlightStatus = CiriumFlightStatus(
    id,
    "TST",
    "TST",
    "TST",
    "1000",
    "TST",
    "LHR",
    CiriumDate(scheduledDate, None),
    CiriumDate(scheduledDate, None),
    "A",
    CiriumStatusSchedule("J"),
    CiriumOperationalTimes(
      Some(CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000"))),
      Some(CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000"))),
      Some(CiriumDate("2019-07-15T09:37:00.000Z", Option("2019-07-15T10:37:00.000"))),
      Some(CiriumDate("2019-07-15T09:37:00.000Z", Option("2019-07-15T10:37:00.000"))),
      Some(CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000"))),
      Some(CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000"))),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None),
    None,
    None,
    List(CiriumCodeshare("CZ", "1000", "L"), CiriumCodeshare("DL", "2000", "L")),
    Some(CiriumAirportResources(None, None, Some("A"), None, None)),
    Seq())
}

