package uk.gov.homeoffice.cirium

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{ TestKit, TestProbe }
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import uk.gov.homeoffice.cirium.actors.CiriumFlightStatusRouterActor
import uk.gov.homeoffice.cirium.services.entities.{ CiriumFlightStatus, CiriumTrackableStatus }
import uk.gov.homeoffice.cirium.services.feed.Cirium

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class CiriumStreamToPortResponseSpec extends TestKit(ActorSystem("testActorSystem", ConfigFactory.empty())) with SpecificationLike {
  sequential
  isolated

  class MockClient(startUri: String)(implicit system: ActorSystem) extends Cirium.Client("", "", startUri) {

    val itemUriRegEx = "https://item/(\\d).+".r

    def sendReceive(endpoint: Uri): Future[HttpResponse] = {

      val res = endpoint.toString() match {
        case "https://latest?appId=&appKey=" =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, initialResponse)))
        case "https://current/previous/2?appId=&appKey=" =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, back1hop)))
        case "https://item/1/next/2?appId=&appKey=" =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward1)))
        case "https://item/3/next/2?appId=&appKey=" =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, forward2)))
        case "https://item/5/next/2?appId=&appKey=" =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, lastResponse)))
        case itemUriRegEx(itemId) =>
          Future(HttpResponse(200, Nil, HttpEntity(ContentTypes.`application/json`, flightStatusResponse(s"XX$itemId", itemId))))
      }

      res
    }
  }

  "Given a stream of messages, each should end up in the correct port" >> {

    val client = new MockClient("https://latest")
    val feed = Cirium.Feed(client, pollEveryMillis = 100)
    val probe = TestProbe()

    implicit val mat: ActorMaterializer = ActorMaterializer()

    val flightStatusActor: ActorRef = system
      .actorOf(CiriumFlightStatusRouterActor.props(Map("TST" -> probe.ref)), "flight-status-actor")

    val result = feed.start(1, 2).map { source =>
      source.runWith(Sink.actorRef(flightStatusActor, "complete"))
    }

    probe.fishForMessage(5 seconds) {
      case CiriumTrackableStatus(s, _, _) if s.arrivalAirportFsCode == "TST" && s.carrierFsCode == "XX5" =>
        true
      case CiriumTrackableStatus(s, _, _) =>
        println(s"Got this ${s.carrierFsCode}, ${s.arrivalAirportFsCode}")
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

  def flightStatusResponse(carrierCode: String, item: String) =
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

