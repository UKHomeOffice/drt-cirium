package uk.gov.homeoffice.cirium

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{ TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import uk.gov.homeoffice.cirium.services.entities._
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class CiriumSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty())) with SpecificationLike {
  sequential
  isolated

  "I should be able to connect to the feed and see what happens" >> {
    skipped("connectivity tester")

    val client = new Cirium.ProdClient(
      sys.env("CIRIUM_APP_ID"),
      sys.env("CIRIUM_APP_KEY"),
      sys.env("CIRIUM_APP_ENTRY_POINT"))
    val feed = new Cirium.Feed(client)
    val probe = TestProbe()

    implicit val mat: ActorMaterializer = ActorMaterializer()

    val result = feed.start(5).map { source =>
      source.runWith(Sink.seq).pipeTo(probe.ref)
    }

    probe.fishForMessage(10 minutes) {
      case x => false
    }
    true
  }

  "I should be able to parse to the initial response" >> {

    val client = new MockClient(initialResponse)
    val result = Await.result(client.initialRequest(), 1 second)

    val expected = CiriumInitialResponse(
      CiriumRequestMetaData("latest", None, None, "https://endpoint/rest/v2/json/latest"),
      "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abcd34")
    result === expected
  }

  "I should be able to parse to the item list response" >> {

    val client = new MockClient(itemListResponse)
    val result = Await.result(client.backwards("test", 2), 1 second)

    val expected = CiriumItemListResponse(
      CiriumRequestMetaData(
        "previous",
        Some(CiriumItemId("2019/08/19/11/01/28/731/FFF", "2019/08/19/11/01/28/731/XXX")),
        Some(CiriumBatchSize("2", 2)),
        "https://endpoint/rest/v2/json/2019/08/19/11/01/28/731/YYY/previous/2"),
      List(
        "https://endpoint/rest/v2/json/2019/08/19/11/01/28/469/FFF",
        "https://endpoint/rest/v2/json/2019/08/19/11/01/28/475/XXX"))

    result === expected
  }

  //  "I should be able to parse a flight status response" >> {
  //
  //    val client = new MockClient(flightStatusResponse)
  //    val result = Await.result(client.requestItem("endpoint"), 1 second)
  //
  //    val expected = CiriumFlightStatusResponse(
  //      CiriumRequestMetaData(
  //        "item",
  //        Some(CiriumItemId("2019/08/14/09/40/39/111/abdde1", "2019/08/14/09/40/39/111/abdde1")),
  //        None,
  //        "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abdde1"),
  //      Option(List(
  //        CiriumFlightStatus(
  //          100000,
  //          "TST",
  //          "TST",
  //          "TST",
  //          "1000",
  //          "TST",
  //          "LHR",
  //          CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000")),
  //          CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000")),
  //          "A",
  //          CiriumOperationalTimes(
  //            Some(CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000"))),
  //            Some(CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000"))),
  //            Some(CiriumDate("2019-07-15T09:37:00.000Z", Option("2019-07-15T10:37:00.000"))),
  //            Some(CiriumDate("2019-07-15T09:37:00.000Z", Option("2019-07-15T10:37:00.000"))),
  //            Some(CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000"))),
  //            Some(CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000"))),
  //            None,
  //            None,
  //            None,
  //            None,
  //            None,
  //            None,
  //            None,
  //            None,
  //            None,
  //            None),
  //          List(CiriumCodeshare("CZ", "1000", "L"), CiriumCodeshare("DL", "2000", "L")),
  //          Some(CiriumAirportResources(None, None, Some("A"), None, None)),
  //          Seq()))))
  //
  //    result === expected
  //  }

  val initialResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "latest",
      |        "url": "https://endpoint/rest/v2/json/latest"
      |    },
      |    "item": "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abcd34"
      |}
    """.stripMargin

  val itemListResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "previous",
      |        "itemId": {
      |            "requested": "2019/08/19/11/01/28/731/FFF",
      |            "interpreted": "2019/08/19/11/01/28/731/XXX"
      |        },
      |        "batchSize": {
      |            "requested": "2",
      |            "interpreted": 2
      |        },
      |        "url": "https://endpoint/rest/v2/json/2019/08/19/11/01/28/731/YYY/previous/2"
      |    },
      |    "items": [
      |        "https://endpoint/rest/v2/json/2019/08/19/11/01/28/469/FFF",
      |        "https://endpoint/rest/v2/json/2019/08/19/11/01/28/475/XXX"
      |    ]
      |}
    """.stripMargin

  val flightStatusResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "item",
      |        "itemId": {
      |            "requested": "2019/08/14/09/40/39/111/abdde1",
      |            "interpreted": "2019/08/14/09/40/39/111/abdde1"
      |        },
      |        "url": "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abdde1"
      |    },
      |    "flightStatuses": [
      |        {
      |            "flightId": 100000,
      |            "carrierFsCode": "TST",
      |            "operatingCarrierFsCode": "TST",
      |            "primaryCarrierFsCode": "TST",
      |            "flightNumber": "1000",
      |            "departureAirportFsCode": "TST",
      |            "arrivalAirportFsCode": "LHR",
      |            "departureDate": {
      |                "dateUtc": "2019-07-15T09:10:00.000Z",
      |                "dateLocal": "2019-07-15T10:10:00.000"
      |            },
      |            "arrivalDate": {
      |                "dateUtc": "2019-07-15T11:05:00.000Z",
      |                "dateLocal": "2019-07-15T13:05:00.000"
      |            },
      |            "status": "A",
      |            "schedule": {
      |                "flightType": "J",
      |                "serviceClasses": "XXXX",
      |                "restrictions": "",
      |                "uplines": [],
      |                "downlines": []
      |            },
      |            "operationalTimes": {
      |                "publishedDeparture": {
      |                    "dateUtc": "2019-07-15T09:10:00.000Z",
      |                    "dateLocal": "2019-07-15T10:10:00.000"
      |                },
      |                "scheduledGateDeparture": {
      |                    "dateUtc": "2019-07-15T09:10:00.000Z",
      |                    "dateLocal": "2019-07-15T10:10:00.000"
      |                },
      |                "estimatedRunwayDeparture": {
      |                    "dateUtc": "2019-07-15T09:37:00.000Z",
      |                    "dateLocal": "2019-07-15T10:37:00.000"
      |                },
      |                "actualRunwayDeparture": {
      |                    "dateUtc": "2019-07-15T09:37:00.000Z",
      |                    "dateLocal": "2019-07-15T10:37:00.000"
      |                },
      |                "publishedArrival": {
      |                    "dateUtc": "2019-07-15T11:05:00.000Z",
      |                    "dateLocal": "2019-07-15T13:05:00.000"
      |                },
      |                "scheduledGateArrival": {
      |                    "dateUtc": "2019-07-15T11:05:00.000Z",
      |                    "dateLocal": "2019-07-15T13:05:00.000"
      |                }
      |            },
      |            "codeshares": [
      |                {
      |                    "fsCode": "CZ",
      |                    "flightNumber": "1000",
      |                    "relationship": "L"
      |                },
      |                {
      |                    "fsCode": "DL",
      |                    "flightNumber": "2000",
      |                    "relationship": "L"
      |                }
      |            ],
      |            "delays": {},
      |            "flightDurations": {
      |                "scheduledBlockMinutes": 115
      |            },
      |            "airportResources": {
      |                "arrivalTerminal": "A"
      |            },
      |            "flightEquipment": {
      |                "scheduledEquipmentIataCode": "XXX",
      |                "actualEquipmentIataCode": "XXX",
      |                "tailNumber": "Z-ZZZZ"
      |            },
      |            "flightStatusUpdates": [],
      |            "irregularOperations": []
      |        }
      |    ]
      |}
    """.stripMargin
}

class MockClient(mockResponse: String)(implicit system: ActorSystem) extends Cirium.Client("", "", "") {

  def sendReceive(endpoint: Uri): Future[HttpResponse] = {
    Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, mockResponse)))
  }
}

