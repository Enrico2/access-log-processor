package com.enricode.datadog

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import com.twitter.util.Time
import scala.collection.JavaConverters._

class StatsBuckets(
  numBuckets: Int,
  hitAlertThreshold: Int = 10,
  srAlert: Option[Double] = None
) {
  require(numBuckets > 0)
  private[this] val n = numBuckets + 1 // number of buckets to maintain + 1 for "current"

  // Index of the current bucket
  private[this] val current = new AtomicInteger(0)

  private[this] val summarizer = new StatsSummarizer

  // circular buffer for all buckets
  private[this] val buckets = Array.fill(n)(new StatsBucket(statsSummarizer = summarizer))

  private[this] def allButCurrentBucket = for (i <- 0 until n if i != current.get()) yield buckets(i)

  // average requests monitor
  private[this] val averageRequestsMonitor = AlertMonitors.averageRequestsMonitor(hitAlertThreshold) {
    // This is ok to run while metrics are coming in because only "current" is updated in real time
    // and we assume the time to calculate the function is less than the granularity of a bucket.
    val totalRequests = allButCurrentBucket.foldLeft(0) { (sum, b) => sum + b.getTotal }
    totalRequests.toDouble / 120
  }

  private[this] val succesRateMonitor = srAlert.map { threshold =>
    AlertMonitors.successRate(threshold) {
      val buckets = allButCurrentBucket
      val total = buckets.foldLeft(0) { (sum, b) => sum + b.getTotal }
      val errors = buckets.foldLeft(0) { (sum, b) => sum + b.getErrors }

      if (total > 0) (total - errors).toFloat * 100 / total else 100
    }
  }

  /**
    * Commits the current bucket, updates the alert monitor and reports metrics to the console.
    */
  def rotateAndReport(): Unit = {
    val now = Time.now

    val completedBucket = buckets(
      current.getAndUpdate { i =>
        val next = (i + 1) % n
        buckets(next).reset()
        next
      }
    )

    averageRequestsMonitor.update(now)
    succesRateMonitor.foreach(_.update(now))

    print("\u001B[1J")
    println(summarizer.summary(completedBucket, Some(averageRequestsMonitor), succesRateMonitor))
  }

  def process(accessLogRecord: AccessLogRecord): Unit = buckets(current.get()).process(accessLogRecord)
}


class StatsBucket(statsSummarizer: StatsSummarizer) {
  private[this] val totalEvents = new AtomicInteger(0)
  private[this] val errors = new AtomicInteger(0)

  private[this] val counters = new ConcurrentHashMap[String, AtomicInteger]()

  private[this] def incr(key: String): Unit = {
    counters.computeIfAbsent(key, _ => new AtomicInteger(0)).incrementAndGet()
  }

  def getTotal: Int = totalEvents.get()
  def getErrors: Int = errors.get()

  def reset(): Unit = {
    counters.clear()
    totalEvents.set(0)
    errors.set(0)
  }

  def process(record: AccessLogRecord): Unit = {
    totalEvents.incrementAndGet()
    if (record.statusCode >= 400) errors.incrementAndGet()

    incr(record.request.section)
  }

  def statsString: String = {
    statsSummarizer.bucketSummary(
      totalEvents.get(),
      errors.get(),
      counters.asScala.mapValues(_.get()).view.force.toMap
    )
  }
}
