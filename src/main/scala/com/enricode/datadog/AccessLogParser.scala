package com.enricode.datadog

import com.enricode.datadog.HttpMethod.HttpMethod
import com.twitter.util.Time

/**
  * Provides a method to parse a standard w3c access log line to a model object.
  */
object AccessLogParser {
  private[this] val regex = {
    val ddd = "\\d{1,3}"
    val ip = s"($ddd\\.$ddd\\.$ddd\\.$ddd)?"
    val client = "(\\S+)"
    val user = "(\\S+)"
    val dateTime = "\\[(.+?)\\]"
    val request = "\"(.*?)\""
    val status = "(\\d{3})"
    val bytes = "(\\S+)"
    val referer = "\"(.*?)\""
    val agent = "\"(.*?)\""

    s"$ip $client $user $dateTime $request $status $bytes $referer $agent".r
  }

  def parseSlim(logLine: String): Option[AccessLogRecord] = {
    try {
      val regex(_, _, _, _, request, statusCode, _, _, _) = logLine
      Some(AccessLogSlimRecord(ClientRequest(request), statusCode.toInt))
    } catch {
      case _: Throwable =>
        println(s"failed parsing line: (($logLine))")
        None
    }
  }

  def parse(logLine: String): Option[AccessLogRecord] = {
    try {
      val regex(clientIp, clientIdentity, user, dateTime, request, statusCode, bytesSent, referer, userAgent) = logLine
      TimeUtil.parseTimestamp(dateTime).map { time =>
        AccessLogFullRecord(
          clientIp,
          clientIdentity,
          user,
          time,
          ClientRequest(request),
          statusCode.toInt,
          if (bytesSent == "-") 0 else bytesSent.toInt,
          referer,
          userAgent
        )
      }
    } catch {
      case _: Throwable =>
        println(s"failed parsing line: (($logLine))")
        None
    }
  }
}

sealed trait AccessLogRecord {
  def request: ClientRequest
  def statusCode: Int
}

case class AccessLogSlimRecord(
  request: ClientRequest,
  statusCode: Int,
) extends AccessLogRecord

case class AccessLogFullRecord(
  clientIp: String,
  clientIdentity: String,
  user: String,
  dateTime: Time,
  request: ClientRequest,
  statusCode: Int,
  bytesSent: Int,
  referer: String,
  userAgent: String
) extends AccessLogRecord

case class ClientRequest(method: HttpMethod, path: String, section: String)

object ClientRequest {
  private[this] def fail(request: String) = {
    val message = s"failed parsing request: $request"
    println(message)
    throw new RuntimeException(message)
  }

  def apply(request: String): ClientRequest = {
    request.split(" ") match {
      case Array(method, path, _) if path.startsWith("/") =>
        val section = path.split('/').toList match {
          case Nil => "/"
          case _ :: s :: _ if s.startsWith("?") || s.startsWith("#") => "/" // for paths like "/?foo=goo" or "/#anchor"
          case _ :: s :: _ => s"/$s"
          case _ => fail(request)
        }

        ClientRequest(HttpMethod.withName(method), path, section)

      case _ =>
        fail(request)
    }
  }
}

object HttpMethod extends Enumeration {
  type HttpMethod = Value
  val GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH = Value
}