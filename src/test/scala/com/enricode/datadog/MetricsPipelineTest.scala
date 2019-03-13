package com.enricode.datadog

import org.scalatest.FunSpec
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}

class MetricsPipelineTest extends FunSpec with MockitoSugar with Eventually {
  implicit val config: PatienceConfig =
    PatienceConfig(scaled(Span(350, Millis)), scaled(Span(25, Millis)))

  private[this] val sample =
    """66.249.73.135 - dude [20/May/2015:12:05:54 +0000] "GET /blog/geekery/comcast-dns-hijack-breaks-things.html HTTP/1.1" 200 18095 "-" "Mozilla/5.0 (iPhone; CPU iPhone OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5376e Safari/8536.25 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)""""
  private[this] val buckets = mock[StatsBuckets]
  private[this] val pipeline = new MetricsPipeline(buckets)

  describe("MetricsPipeline") {
    it("processes lines as they're requested") {
      pipeline.process(sample)
      val record = AccessLogParser.parse(sample).getOrElse(fail())
      verify(buckets).process(record)

      pipeline.process(sample)

      eventually {
        verify(buckets, times(2)).process(record)
      }

      pipeline.process(sample)

      eventually {
        verify(buckets, times(3)).process(record)
      }
    }
  }
}
