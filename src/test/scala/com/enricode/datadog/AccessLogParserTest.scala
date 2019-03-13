package com.enricode.datadog

import org.scalatest.FunSpec

class AccessLogParserTest extends FunSpec {
  private[this] val sample =
    """66.249.73.135 - dude [20/May/2015:12:05:54 +0000] "GET /blog/geekery/comcast-dns-hijack-breaks-things.html HTTP/1.1" 200 18095 "-" "Mozilla/5.0 (iPhone; CPU iPhone OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5376e Safari/8536.25 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)""""

  describe("AccessLogParser") {
    it("parses a correctly formed log line to a record") {
      AccessLogParser.parse(sample) match {
        case None => fail()
        case Some(record: AccessLogFullRecord) =>
          assert(record.clientIp == "66.249.73.135")
          assert(record.clientIdentity == "-")
          assert(record.user == "dude")
          assert(TimeUtil.parseTimestamp("20/May/2015:12:05:54 +0000").contains(record.dateTime))
          assert(record.request == ClientRequest(HttpMethod.GET, "/blog/geekery/comcast-dns-hijack-breaks-things.html", "/blog"))
          assert(record.statusCode == 200)
          assert(record.bytesSent == 18095)
          assert(record.referer == "-")
          assert(record.userAgent == "Mozilla/5.0 (iPhone; CPU iPhone OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5376e Safari/8536.25 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
        case _ => fail()
      }
    }
  }
}
