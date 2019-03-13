package com.enricode.datadog

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}
import com.twitter.util.Time

object TimeUtil {
  private[this] val dateFormat = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")

  def parseTimestamp(dateTime: String): Option[Time] = {
    try {
      Some(Time.fromMilliseconds(ZonedDateTime.parse(dateTime, dateFormat).toEpochSecond * 1000))
    } catch {
      case e: Throwable =>
        println(s"failed parsing time: $dateTime", e)
        None
    }
  }

  def format(time: Time): String = {
    val instant = Instant.ofEpochMilli(time.inMilliseconds)
    val zoneId = ZoneId.of("UTC")
    dateFormat.format(ZonedDateTime.ofInstant(instant, zoneId))
  }
}
