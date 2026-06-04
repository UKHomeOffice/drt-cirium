package uk.gov.homeoffice.cirium

import org.apache.pekko.http.scaladsl.model.{HttpResponse, Uri}
import uk.gov.homeoffice.cirium.services.entities.{CiriumFlightStatusResponseSuccess, CiriumInitialResponse, CiriumItemListResponse, CiriumRequestMetaData}
import uk.gov.homeoffice.cirium.services.feed.CiriumClientLike

import scala.concurrent.{ExecutionContext, Future}


/** Mock client that supports only initialRequest with a fixed latest item link. */
case class MockClientWithInitialResponseOnly(firstItemLink: String)
                                            (implicit ec: ExecutionContext) extends CiriumClientLike {

  override def initialRequest(): Future[CiriumInitialResponse] = Future(
    CiriumInitialResponse(CiriumRequestMetaData("", None, None, ""), firstItemLink))

  override def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] = ???

  override def forwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] = ???

  override def makeRequest(endpoint: String, maybeMaxRetries: Option[Int]): Future[HttpResponse] = ???

  override def sendReceive(uri: Uri): Future[HttpResponse] = ???

  override def fetchFlightStatus(endpoint: String): Future[CiriumFlightStatusResponseSuccess] = ???
}

/** Mock client that always fails initialRequest to simulate connectivity loss. */
case class MockClientWithFailure(firstItemLink: String)
                                (implicit ec: ExecutionContext)extends CiriumClientLike {

  override def initialRequest(): Future[CiriumInitialResponse] = Future(throw new Exception("Unable to connect"))

  override def backwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] = ???

  override def forwards(latestItemLocation: String, step: Int): Future[CiriumItemListResponse] = ???

  override def makeRequest(endpoint: String, maybeMaxRetries: Option[Int]): Future[HttpResponse] = ???

  override def sendReceive(uri: Uri): Future[HttpResponse] = ???

  override def fetchFlightStatus(endpoint: String): Future[CiriumFlightStatusResponseSuccess] = ???
}
