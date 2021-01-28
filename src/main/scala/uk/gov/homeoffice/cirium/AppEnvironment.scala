package uk.gov.homeoffice.cirium

object AppEnvironment {

  lazy val goBackHops = sys.env.getOrElse("CIRIUM_GO_BACK_X_1000", "5").toInt

  lazy val portCodes: Array[String] = sys.env("PORT_CODES").split(",")

  lazy val pollMillis = sys.env.getOrElse("CIRIUM_POLL_MILLIS", "5000").toInt

  lazy val statusRetentionDurationHours = sys.env.getOrElse("CIRIUM_STATUS_RETENTION_DURATION_HOURS", "24").toInt

  lazy val cirium_app_Id = sys.env("CIRIUM_APP_ID")

  lazy val cirium_app_key = sys.env("CIRIUM_APP_KEY")

  lazy val cirium_app_entry_point = sys.env("CIRIUM_APP_ENTRY_POINT")

  lazy val cirium_message_latency_tolerance = sys.env.getOrElse("CIRIUM_MESSAGE_LATENCY_TOLERANCE_SECONDS", "60").toInt

  lazy val cirium_lost_connect_tolerance = sys.env.getOrElse("CIRIUM_LOST_CONNECTION_TOLERANCE_SECONDS", "300").toInt

}