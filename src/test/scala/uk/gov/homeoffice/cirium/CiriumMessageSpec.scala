package uk.gov.homeoffice.cirium

import org.joda.time.DateTime
import org.specs2.mutable.Specification
import uk.gov.homeoffice.cirium.services.entities.{ CiriumDate, CiriumFlightStatus, CiriumMessageFormat, CiriumOperationalTimes, CiriumTrackableStatus }

import scala.util.{ Failure, Success }

class CiriumMessageSpec extends Specification {

  val ciriumStatus = CiriumFlightStatus(
    100000,
    "",
    "",
    "",
    "",
    "",
    "",
    CiriumDate("2019-07-15T09:10:00.000Z", None),
    CiriumDate("2019-07-15T11:05:00.000Z", None),
    "",
    CiriumOperationalTimes(
      None,
      None,
      None,
      None,
      None,
      None,
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
    List(),
    None,
    Seq())

  "Given a Cirium message URL I should be able to determine its timestamp" >> {
    val uri = "https://something.something.endpoint/rest/v2/json/2020/02/12/12/32/17/751/hash"

    val result = CiriumMessageFormat.dateFromUri(uri) match {
      case Success(res) => res
    }

    result === new DateTime(2020, 2, 12, 12, 32, 17)
  }

  "Given a bad Cirium message URL I should recover gracefully" >> {
    val uri = "https://something.something.endpoint/rest/v2/json/error/02/12/12/32/17/751/hash"

    val result = CiriumMessageFormat.dateFromUri(uri)

    result.isInstanceOf[Failure[DateTime]]
  }

  "Given a Cirium message that was issued within the threshold limit of the processing time it should be shown as in sync" >> {

    val uri = "https://something.something.endpoint/rest/v2/json/2020/02/12/12/32/17/751/hash"
    val processedTime = new DateTime(2020, 2, 12, 12, 32, 17)
    val trackableStatus = CiriumTrackableStatus(ciriumStatus, uri, processedTime.getMillis)

    val result = trackableStatus.isInSync()

    result === true
  }

  "Given a Cirium message that was issued before the threshold limit of the processing time it should be shown as not in sync" >> {

    val uri = "https://something.something.endpoint/rest/v2/json/2020/02/12/12/32/17/751/hash"
    val processedTime = new DateTime(2020, 2, 12, 12, 38, 17)
    val trackableStatus = CiriumTrackableStatus(ciriumStatus, uri, processedTime.getMillis)

    val result = trackableStatus.isInSync()

    result === false
  }

}
