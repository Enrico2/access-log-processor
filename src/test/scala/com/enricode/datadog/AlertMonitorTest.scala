package com.enricode.datadog

import org.scalatest.FunSpec
import org.scalatest.mockito.MockitoSugar

class AlertMonitorTest extends FunSpec with MockitoSugar {

  trait Fixture {
    val time = "20/May/2015:12:05:54 +0000"
    val now = TimeUtil.parseTimestamp(time).getOrElse(fail())
    val threshold = 10

    var getValue = 0

    val monitor = AlertMonitors.averageRequestsMonitor(threshold)(getValue)
  }

  describe("AlertMonitor") {

    it("messages is empty when no updates have been called") {
      new Fixture {
        assert(monitor.messages.isEmpty)
      }
    }

    it("messages is empty when update was called with 0") {
      new Fixture {
        monitor.update(now)
        assert(monitor.messages.isEmpty)
      }
    }

    it(s"messages is empty when update was called with value below threshold") {
      new Fixture {
        getValue = threshold - 1

        monitor.update(now)
        assert(monitor.messages.isEmpty)
      }
    }

    it(s"messages has information about alert and persists") {
      new Fixture {
        val aboveThreshold = threshold + 1
        getValue = aboveThreshold

        monitor.update(now)

        val line1 = s"$time - High traffic generated an alert - hits = $aboveThreshold.0 rps, triggered at $time"
        val line3 = s"$time - High traffic alert recovered - hits = ${threshold - 1}.0 rps, recovered at $time"

        assert(monitor.messages == Seq(line1))

        monitor.update(now)

        assert(monitor.messages == Seq(line1))

        getValue = threshold - 1
        monitor.update(now)
        assert(monitor.messages == Seq(line3, line1))

        monitor.update(now)
        assert(monitor.messages == Seq(line3, line1))
      }
    }
  }
}
