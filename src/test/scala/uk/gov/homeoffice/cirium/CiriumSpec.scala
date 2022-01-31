package uk.gov.homeoffice.cirium

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterEach
import uk.gov.homeoffice.cirium.services.entities._
import uk.gov.homeoffice.cirium.services.feed.{BackwardsStrategy, Cirium}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class MockBackwardsStrategy(url: String) extends BackwardsStrategy {
  override def backUntil(startItem: String)(implicit executionContext: ExecutionContext): Future[String] = Future.successful(url)
}

class CiriumSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty()))
  with SpecificationLike
  with AfterEach {
  sequential
  isolated

  override def after: Unit = TestKit.shutdownActorSystem(system)

  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec  = mat.executionContext

  "I should be able to connect to the feed and see what happens" >> {
    skipped("connectivity tester")

    val client = new Cirium.ProdClient(
      sys.env("CIRIUM_APP_ID"),
      sys.env("CIRIUM_APP_KEY"),
      sys.env("CIRIUM_APP_ENTRY_POINT"),
      MockMetricsCollector)
    val feed = Cirium.Feed(client, pollEveryMillis = 100, MockBackwardsStrategy("https://item/1"))
    val probe = TestProbe()

    feed.start(1000).map { source =>
      source.runWith(Sink.seq).pipeTo(probe.ref)
    }

    probe.fishForMessage(10.minutes) {
      case _ => false
    }
    true
  }

  "I should be able to parse to the initial response" >> {

    val client = new MockClient(initialResponse, MockMetricsCollector)
    val result = Await.result(client.initialRequest(), 1.second)

    val expected = CiriumInitialResponse(
      CiriumRequestMetaData("latest", None, None, "https://endpoint/rest/v2/json/latest"),
      "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abcd34")
    result === expected
  }

  "I should be able to parse to the item list response" >> {

    val client = new MockClient(itemListResponse, MockMetricsCollector)
    val result = Await.result(client.backwards("test", 2), 1.second)

    val expected = CiriumItemListResponse(List(
      "https://endpoint/rest/v2/json/2019/08/19/11/01/28/469/FFF",
      "https://endpoint/rest/v2/json/2019/08/19/11/01/28/475/XXX"))

    result === expected
  }

  "I should be able to parse a flight status response" >> {

    val client = new MockClient(flightStatusResponse, MockMetricsCollector)
    val result = Await.result(client.requestItem("endpoint"), 1.second)

    val expected = CiriumFlightStatusResponseSuccess(
      CiriumRequestMetaData(
        "item",
        Some(CiriumItemId("2019/08/14/09/40/39/111/abdde1", "2019/08/14/09/40/39/111/abdde1")),
        None,
        "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abdde1"),
      Option(List(
        CiriumFlightStatus(
          100000,
          "TST",
          "TST",
          "TST",
          "1000",
          "TST",
          "LHR",
          CiriumDate("2019-07-15T09:10:00.000Z", Option("2019-07-15T10:10:00.000")),
          CiriumDate("2019-07-15T11:05:00.000Z", Option("2019-07-15T13:05:00.000")),
          "A",
          CiriumOperationalTimes(
            Some(CiriumDate("2019-07-15T09:10:00.000Z", Some("2019-07-15T10:10:00.000"), 1563181800000L)),
            Some(CiriumDate("2019-07-15T09:10:00.000Z", Some("2019-07-15T10:10:00.000"), 1563181800000L)),
            None,
            None,
            None,
            None,
            Some(CiriumDate("2019-07-15T09:37:00.000Z", Some("2019-07-15T10:37:00.000"), 1563183420000L)),
            Some(CiriumDate("2019-07-15T09:37:00.000Z", Some("2019-07-15T10:37:00.000"), 1563183420000L)),
            Some(CiriumDate("2019-07-15T11:05:00.000Z", Some("2019-07-15T13:05:00.000"), 1563188700000L)),
            None,
            Some(CiriumDate("2019-07-15T11:05:00.000Z", Some("2019-07-15T13:05:00.000"), 1563188700000L)),
            None,
            None,
            None,
            None,
            None),
          Option(CiriumDelays(
            departureGateDelayMinutes = Option(5),
            departureRunwayDelayMinutes = None,
            arrivalGateDelayMinutes = Option(6),
            arrivalRunwayDelayMinutes = None)),
          Some(CiriumFlightDurations(
            scheduledBlockMinutes = Some(115),
            blockMinutes = None,
            scheduledAirMinutes = None,
            airMinutes = None,
            scheduledTaxiOutMinutes = None,
            taxiOutMinutes = None,
            scheduledTaxiInMinutes = None,
            taxiInMinutes = None)),
          List(CiriumCodeshare("CZ", "1000", "L"), CiriumCodeshare("DL", "2000", "L")),
          Some(CiriumAirportResources(None, None, Some("A"), None, None)),
          Seq()))))

    result === expected
  }

  "I should get exception while parsing response that does not have request object" >> {
    val client = new MockClient(flightStatusResponseWithoutRequestObject, MockMetricsCollector)
    val result = Await.result(client.requestItem("endpoint"), 1.second)
    result.isInstanceOf[CiriumFlightStatusResponseFailure]
  }

  def initialResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "latest",
      |        "url": "https://endpoint/rest/v2/json/latest"
      |    },
      |    "item": "https://endpoint/rest/v2/json/2019/08/14/09/40/39/111/abcd34"
      |}
    """.stripMargin

  def itemListResponse: String =
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

  def flightStatusResponse: String =
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
      |            "delays": {
      |                "departureGateDelayMinutes": 5,
      |                "arrivalGateDelayMinutes": 6
      |            },
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

  def flightStatusResponseWithoutRequestObject: String =
    """
      |{
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
      |            "delays": {
      |                "departureGateDelayMinutes": 5,
      |                "arrivalGateDelayMinutes": 6
      |            },
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

class MockClient(mockResponse: String, metricsCollector: MetricsCollector)(implicit system: ActorSystem) extends Cirium.Client("", "", "", metricsCollector) {
  def sendReceive(endpoint: Uri)(implicit executionContext: ExecutionContext): Future[HttpResponse] = {
    Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, mockResponse)))(executionContext)
  }
}
