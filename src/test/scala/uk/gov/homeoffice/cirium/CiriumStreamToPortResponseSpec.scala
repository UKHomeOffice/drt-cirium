package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.testkit.{ TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterEach
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor
import uk.gov.homeoffice.cirium.services.entities.CiriumTrackableStatus
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.matching.Regex

class CiriumStreamToPortResponseSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty()))
  with SpecificationLike with AfterEach {
  sequential
  isolated

  override def after: Unit = TestKit.shutdownActorSystem(system)

  class MockClient(startUri: String)(implicit system: ActorSystem) extends Cirium.Client("", "", startUri) {

    val latestRegex: Regex = "https://latest.+".r
    val previousRegex: Regex = "https://current/previous/.+".r
    val forward1Regex: Regex = "https://item/1/.+".r
    val forward3Regex: Regex = "https://item/3/.+".r
    val forward5Regex: Regex = "https://item/5/.+".r
    val itemUriRegEx: Regex = "https://item/(\\d).+".r

    def sendReceive(endpoint: Uri): Future[HttpResponse] = {

      val res = endpoint.toString() match {
        case latestRegex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, initialResponse)))
        case previousRegex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, back1hop)))
        case forward1Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward1)))
        case forward3Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward2)))
        case forward5Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, lastResponse)))
        case itemUriRegEx(itemId) =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, flightStatusResponse(s"XX$itemId", itemId))))
        case u =>
          Future.failed(new Exception("hmm"))
      }

      res
    }
  }

  class MockClientWithoutRequestObjectInResponse(startUri: String)(implicit system: ActorSystem) extends Cirium.Client("", "", startUri) {

    val latestRegex: Regex = "https://latest.+".r
    val previousRegex: Regex = "https://current/previous/.+".r
    val forward1Regex: Regex = "https://item/1/.+".r
    val forward3Regex: Regex = "https://item/3/.+".r
    val forward5Regex: Regex = "https://item/5/.+".r
    val itemUriRegEx: Regex = "https://item/(\\d).+".r

    def sendReceive(endpoint: Uri): Future[HttpResponse] = {

      val res = endpoint.toString() match {
        case latestRegex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, initialResponse)))
        case previousRegex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, back1WithoutRequestObjectHop)))
        case forward1Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forwardWithoutRequestObject1)))
        case forward3Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward2)))
        case forward5Regex() =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, lastResponse)))
        case itemUriRegEx(itemId) =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, flightStatusResponseWithoutRequestObject(s"XX$itemId", itemId))))
        case u =>
          Future.failed(new Exception("hmm"))
      }

      res
    }
  }

  class MockClientWithFailures(startUri: String)(implicit system: ActorSystem) extends Cirium.Client("", "", startUri) {

    val itemUriRegEx: Regex = "https://item/(\\d).+".r

    var calls = 0

    def sendReceive(endpoint: Uri): Future[HttpResponse] = Future {

      endpoint.toString() match {
        case "https://latest?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, initialResponse))
        case "https://current/previous/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, back1hop))
        case "https://item/1/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward1))
        case "https://item/3/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward2))
        case "https://item/5/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, lastResponse))
        case "https://item/5?appId=&appKey=" if calls == 0 =>
          calls += 1
          throw new Exception("Failed to connect")
        case itemUriRegEx(itemId) =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, flightStatusResponse(s"XX$itemId", itemId)))
      }
    }
  }

  class MockClientWithInvalidJson(startUri: String)(implicit system: ActorSystem) extends Cirium.Client("", "", startUri) {

    val itemUriRegEx: Regex = "https://item/(\\d).+".r

    def sendReceive(endpoint: Uri): Future[HttpResponse] = Future {

      endpoint.toString() match {
        case "https://latest?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, initialResponse))
        case "https://current/previous/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, back1hop))
        case "https://item/1/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward1))
        case "https://item/3/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward2))
        case "https://item/5/next/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, lastResponse))
        case "https://item/2?appId=&appKey=" =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, invalidJson))
        case itemUriRegEx(itemId) =>
          HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, flightStatusResponse(s"XX$itemId", itemId)))
      }
    }
  }

  "Given a stream of messages, each should end up in the correct port" >> {

    val client = new MockClient("https://latest")
    val feed = Cirium.Feed(client, pollEveryMillis = 100, MockBackwardsStrategy("https://item/1"))
    val probe = TestProbe()

    implicit val mat: Materializer = Materializer.createMaterializer(system)

    val flightStatusActor: ActorRef = system
      .actorOf(CiriumFlightStatusRouterActor.props(Map("TST" -> probe.ref)), "flight-status-actor")

    feed.start(2).map { source =>
      source.runWith(Sink.actorRef(flightStatusActor, "complete", t => println(s"Failed with $t")))
    }

    probe.fishForMessage(5.seconds) {
      case CiriumTrackableStatus(s, _, _) if s.arrivalAirportFsCode == "TST" && s.carrierFsCode == "XX5" =>
        true
      case CiriumTrackableStatus(s, _, _) =>
        false
    }

    success
  }

  "Given a stream of messages, each should end up in the correct port even if request object is missing in json response" >> {

    val client = new MockClientWithoutRequestObjectInResponse("https://latest")
    val feed = Cirium.Feed(client, pollEveryMillis = 100, MockBackwardsStrategy("https://item/1"))
    val probe = TestProbe()

    implicit val mat: Materializer = Materializer.createMaterializer(system)

    val flightStatusActor: ActorRef = system
      .actorOf(CiriumFlightStatusRouterActor.props(Map("TST" -> probe.ref)), "flight-status-actor")

    feed.start(2).map { source =>
      source.runWith(Sink.actorRef(flightStatusActor, "complete", t => println(s"Failed with $t")))
    }

    probe.fishForMessage(5.seconds) {
      case CiriumTrackableStatus(s, _, _) if s.arrivalAirportFsCode == "TST" && s.carrierFsCode == "XX5" =>
        true
      case CiriumTrackableStatus(s, _, _) =>
        false
    }

    success
  }

  "Given a network failure, the failed request should retry" >> {

    val client = new MockClientWithFailures("https://latest")
    val feed = Cirium.Feed(client, pollEveryMillis = 100, MockBackwardsStrategy("https://item/1"))
    val probe = TestProbe()

    implicit val mat: Materializer = Materializer.createMaterializer(system)

    val flightStatusActor: ActorRef = system
      .actorOf(CiriumFlightStatusRouterActor.props(Map("TST" -> probe.ref)), "flight-status-actor")

    feed.start(2).map { source =>
      source.runWith(Sink.actorRef(flightStatusActor, "complete", t => println(s"Failed with $t")))
    }

    probe.fishForMessage(5.seconds) {
      case CiriumTrackableStatus(s, _, _) if s.arrivalAirportFsCode == "TST" && s.carrierFsCode == "XX5" =>
        true
      case CiriumTrackableStatus(s, _, _) =>
        false
    }

    success
  }

  "Given an item with invalid json, and an item with valid json, I should see the valid item in the sink." >> {

    val client = new MockClientWithInvalidJson("https://latest")
    val feed = Cirium.Feed(client, pollEveryMillis = 100, MockBackwardsStrategy("https://item/1"))
    val probe = TestProbe()

    implicit val mat: Materializer = Materializer.createMaterializer(system)

    val flightStatusActor: ActorRef = system
      .actorOf(CiriumFlightStatusRouterActor.props(Map("TST" -> probe.ref)), "flight-status-actor")

    feed.start(2).map { source =>
      source.runWith(Sink.actorRef(flightStatusActor, "complete", t => println(s"Failed with $t")))
    }

    probe.fishForMessage(5.seconds) {
      case CiriumTrackableStatus(s, _, _) if s.arrivalAirportFsCode == "TST" && s.carrierFsCode == "XX5" =>
        true
      case CiriumTrackableStatus(s, _, _) =>
        false
    }

    success
  }

  val initialResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "latest",
      |        "url": "https://latest"
      |    },
      |    "item": "https://current"
      |}
    """.stripMargin

  val back1WithoutRequestObjectHop: String =
    """
      |{
      |    "items": [
      |        "https://item/1",
      |        "https://item/2"
      |    ]
      |}
    """.stripMargin

  val back1hop: String =
    """
      |{
      |    "request": {
      |        "endpoint": "previous",
      |        "itemId": {
      |            "requested": "FFF",
      |            "interpreted": "FFF"
      |        },
      |        "batchSize": {
      |            "requested": "2",
      |            "interpreted": 2
      |        },
      |        "url": "https://current/previous/2"
      |    },
      |    "items": [
      |        "https://item/1",
      |        "https://item/2"
      |    ]
      |}
    """.stripMargin

  val forwardWithoutRequestObject1: String =
    """
      |{
      |    "items": [
      |        "https://item/2",
      |        "https://item/3"
      |    ]
      |}
    """.stripMargin

  val forward1: String =
    """
      |{
      |    "request": {
      |        "endpoint": "next",
      |        "itemId": {
      |            "requested": "FFF",
      |            "interpreted": "FFF"
      |        },
      |        "batchSize": {
      |            "requested": "2",
      |            "interpreted": 2
      |        },
      |        "url": "https://item/1/next/2"
      |    },
      |    "items": [
      |        "https://item/2",
      |        "https://item/3"
      |    ]
      |}
    """.stripMargin

  val forward2: String =
    """
      |{
      |    "request": {
      |        "endpoint": "next",
      |        "itemId": {
      |            "requested": "FFF",
      |            "interpreted": "FFF"
      |        },
      |        "batchSize": {
      |            "requested": "2",
      |            "interpreted": 2
      |        },
      |        "url": "https://item/3/next/2"
      |    },
      |    "items": [
      |        "https://item/4",
      |        "https://item/5"
      |    ]
      |}
    """.stripMargin

  val lastResponse: String =
    """
      |{
      |    "request": {
      |        "endpoint": "next",
      |        "itemId": {
      |            "requested": "FFF",
      |            "interpreted": "FFF"
      |        },
      |        "batchSize": {
      |            "requested": "2",
      |            "interpreted": 2
      |        },
      |        "url": "https://item/3/next/2"
      |    },
      |    "items": []
      |}
    """.stripMargin

  def invalidJson: String =
    s"""
       |{
       |    "request": {
       |        "endpoint": "1",
       |        "itemId": {
       |            "requested": "1",
       |            "interpreted": "1"
       |        },
       |        "url": "https://item/1"
       |    },
       |    "flightStatuses": [
       |        {
       |            "flightId": 100000,
       |            "carrierFsCode": "ERROR",
       |            "operatingCarrierFsCode": "ERROR",
       |            "primaryCarrierFsCode": "ERROR",
       |            "flightStatusUpdates": [],
       |            "irregularOperations": []
       |        }
       |    ]
       |}
    """.stripMargin

  def flightStatusResponseWithoutRequestObject(carrierCode: String, item: String): String =
    s"""
       |{
       |    "flightStatuses": [
       |        {
       |            "flightId": 100000,
       |            "carrierFsCode": "$carrierCode",
       |            "operatingCarrierFsCode": "$carrierCode",
       |            "primaryCarrierFsCode": "$carrierCode",
       |            "flightNumber": "1000",
       |            "departureAirportFsCode": "$carrierCode",
       |            "arrivalAirportFsCode": "TST",
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

  def flightStatusResponse(carrierCode: String, item: String): String =
    s"""
       |{
       |    "request": {
       |        "endpoint": "$item",
       |        "itemId": {
       |            "requested": "$item",
       |            "interpreted": "$item"
       |        },
       |        "url": "https://item/$item"
       |    },
       |    "flightStatuses": [
       |        {
       |            "flightId": 100000,
       |            "carrierFsCode": "$carrierCode",
       |            "operatingCarrierFsCode": "$carrierCode",
       |            "primaryCarrierFsCode": "$carrierCode",
       |            "flightNumber": "1000",
       |            "departureAirportFsCode": "$carrierCode",
       |            "arrivalAirportFsCode": "TST",
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
