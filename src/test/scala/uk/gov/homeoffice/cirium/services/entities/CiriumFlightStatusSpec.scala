package uk.gov.homeoffice.cirium.services.entities

import org.joda.time.DateTime
import org.specs2.mutable.Specification

import scala.concurrent.duration.DurationInt

class CiriumFlightStatusSpec extends Specification {
  val scheduled = "2022-06-01T00:00"
  val estimatedRunway = "2022-06-01T00:01"
  val actualRunway = "2022-06-01T00:02"
  val estimatedGate = "2022-06-01T00:03"
  val actualGate = "2022-06-01T00:04"

  "A CiriumFlightStatus" should {
    "Give an estimated time based on its est runway time when it has one" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estRunway = estimatedRunway)
      cfs.estimated should ===(Option(DateTime.parse(estimatedRunway).getMillis))
    }
    "Give an estimated time based on its est gate time minus 5 mins when it has one and doesn't have an est runway" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estGate = estimatedGate)
      cfs.estimated should ===(Option(DateTime.parse(estimatedGate).minus(5.minutes.toMillis).getMillis))
    }
    "Give no estimated time when neither of its estimates are different to the scheduled time" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estRunway = scheduled, estGate = scheduled)
      cfs.estimated should ===(None)
    }

    "Give an actual touchdown when it has an actual runway" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, actRunway = actualRunway)
      cfs.actualTouchdown should ===(Option(DateTime.parse(actualRunway).getMillis))
    }
    "Give no actual touchdown when it has no actual runway" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, actRunway = "")
      cfs.actualTouchdown should ===(None)
    }

    "Give no estimated chox when it has no estimated gate" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estGate = "")
      cfs.estimatedChox should ===(None)
    }
    "Give no estimated time when its estimated gate time is the same as scheduled" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estGate = scheduled)
      cfs.estimatedChox should ===(None)
    }
    "Give an estimated time when its estimated gate time is different to scheduled" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, estGate = estimatedGate)
      cfs.estimatedChox should ===(Option(DateTime.parse(estimatedGate).getMillis))
    }

    "Give an actual chox when it has an actual gate" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, actGate = actualGate)
      cfs.actualChox should ===(Option(DateTime.parse(actualGate).getMillis))
    }
    "Give no actual chox when it has no actual gate" in {
      val cfs = Generator.ciriumFlightStatus(sch = scheduled, actGate = "")
      cfs.actualChox should ===(None)
    }
  }
}
