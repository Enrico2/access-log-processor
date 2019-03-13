package com.enricode.datadog

import java.io.{Closeable, File}
import com.twitter.util.{Duration, JavaTimer}
import org.rogach.scallop.ScallopConf
import sun.misc.Signal
import scala.collection.mutable

object Main {

  private[this] val timer = new JavaTimer(isDaemon = true)
  private[this] val closeables = mutable.Queue[Closeable]()

  Signal.handle(new Signal("INT"), (_: Signal) => {
    println(s"Ctrl+C detected, existing.")
    closeables.foreach(_.close())
    sys.exit(0)
  })

  private[this] def logFile(path: String) = {
    if (path.startsWith("/")) {
      path
    } else {
      s"${new File(".").getCanonicalPath}/$path"
    }
  }

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)

    val updateInterval = conf.updateInterval()
    val buckets = new StatsBuckets(
      12, // One bucket for each 10 seconds + 1 for "current"
      hitAlertThreshold = conf.hitsAlertThreshold(),
      srAlert = if (conf.exprimental()) Some(99) else None
    )

    val timerTask = timer.schedule(Duration.fromSeconds(updateInterval))(buckets.rotateAndReport())

    val pipeline = new MetricsPipeline(buckets, slimParse = conf.exprimental())

    val filePath = logFile(conf.logfile())

    val fileReader = if (conf.testRun()) {
      FollowingLineStream.testSlowStream(filePath, 100, 750) { line =>
        pipeline.process(line)
      }
    } else {
      FollowingLineStream(filePath) { line => pipeline.process(line) }
    }

    closeables.enqueue(() => pipeline.stop())
    closeables.enqueue(() => fileReader.stop())
    closeables.enqueue(() => timerTask.cancel())

    println(s"Starting to collect metrics, first report will appear in $updateInterval seconds..")
    fileReader.start()
  }
}

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val logfile = opt[String](
    default = Some("/tmp/access.log"),
    descr = "Path to w3c formatted access log (default: /tmp/access.log")

  val hitsAlertThreshold = opt[Int](
    default = Some(10),
    descr = "Alert if average rps is bigger than the given integer (default: 10)")

  val testRun = opt[Boolean](
    default = Some(false),
    descr = "For development purposes, slows down reading the log. (default: false)")

  val updateInterval = opt[Int](
    validate = _ > 0,
    default = Some(10),
    descr = "Interval in seconds to update alerts, update display and rotate buckets (default: 10)")

  val exprimental = opt[Boolean](
    default = Some(false),
    descr = "Enable experimental features (last minute and un-tested, see README.) (default: false)"
  )

  verify()
}
