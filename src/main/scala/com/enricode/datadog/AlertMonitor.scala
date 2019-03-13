package com.enricode.datadog

import java.util.concurrent.atomic.AtomicBoolean
import com.twitter.util.Time
import scala.collection.mutable

/**
  * Generic alert monitor that maintains an internal state of the current alarm and a history of
  * alarm messages based on the given parameters. State is updated when `update` is called.
  *
  * Thread safety depends on the thread safety of the given `getValue` method. So, assume not thread safe.
  *
  * @param name the name of the alarm
  * @param getValue a function to calculate the value of the metric this is monitoring
  * @param predicate a predicate that should return true of the metric is in breach
  * @param entity the metric entity (e.g. "hits")
  * @param units the units for the metric (e.g. "rps", "%")
  */
class AlertMonitor(
  name: String,
  getValue: => Double,
  predicate: Double => Boolean,
  entity: String,
  units: String
) {
  private[this] val inAlert = new AtomicBoolean(false)
  private[this] val alertMessages = mutable.Stack[String]()

  def messages: Seq[String] = alertMessages.toIndexedSeq

  def update(now: Time): Unit = {
    val time = TimeUtil.format(now)

    val value = getValue

    if (predicate(value)) {
      val newAlert = !inAlert.getAndSet(true)
      if (newAlert) {
        alertMessages.push(s"$time - $name generated an alert - $entity = $value $units, triggered at $time")
      }
    } else {
      if (inAlert.getAndSet(false)) {
        alertMessages.push(s"$time - $name alert recovered - $entity = $value $units, recovered at $time")
      }
    }
  }
}

object AlertMonitor {
  def apply(
    name: String,
    entity: String,
    units: String,
    predicate: Double => Boolean
  )(
    getValue: => Double
  ): AlertMonitor = new AlertMonitor(name, getValue, predicate, entity, units)
}

object AlertMonitors {
  def averageRequestsMonitor(hitAlertThreshold: Double)(getValue: => Double): AlertMonitor = {
    AlertMonitor("High traffic", "hits", "rps", _ >= hitAlertThreshold)(getValue)
  }

  def successRate(threshold: Double)(getValue: => Double): AlertMonitor = {
    AlertMonitor("Success rate", "success", "%", _ <= threshold)(getValue)
  }
}