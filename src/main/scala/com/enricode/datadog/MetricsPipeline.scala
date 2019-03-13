package com.enricode.datadog

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import scala.concurrent.ExecutionContextExecutor

/**
  * The implementation of stream processing using Akka streams.
  * A single pipeline branch looks like
  * [parse log line to object] -> [filter failed parsings] -> [process log line in current bucket]
  *
  * Parallelization is achieved by building a computation graph (an Akka stream) that has a queue source
  * and multiple workers (parallelism = # cores), where each worker implements a single pipeline.
  * Events are not necessarily processed in order since some workers can be faster than others.
  * However, metrics are aggregated over an entire bucket, so order does not matter.
  *
  */
class MetricsPipeline(buckets: StatsBuckets, slimParse: Boolean = false) {
  private[this] val WorkerCount: Int = Runtime.getRuntime.availableProcessors

  implicit private[this] val system: ActorSystem = ActorSystem("AccessLogProcessor")
  implicit private[this] val materializer: ActorMaterializer = ActorMaterializer()
  implicit private[this] val ec: ExecutionContextExecutor = system.dispatcher

  private[this] val source = Source.queue[String](Int.MaxValue, OverflowStrategy.fail)

  private[this] val lineToRecord = Flow[String].map(AccessLogParser.parse)
  private[this] val optionToDefined = Flow[Option[AccessLogRecord]].collect { case Some(r) => r }
  private[this] val process = Flow[AccessLogRecord].map(buckets.process)

  private[this] val parallelWorkers = parallelize(lineToRecord.via(optionToDefined).via(process), WorkerCount)

  private[this] val pipeline =
    source
      .via(parallelWorkers)
      .to(Sink.ignore)
      .run()

  def process(logLine: String): Unit = {
    pipeline.offer(logLine)
  }

  def stop(): Unit = {
    pipeline.complete()
  }

  private[this] def parallelize[In, Out](worker: Flow[In, Out, Any], workerCount: Int): Flow[In, Out, NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      val balancer = b.add(Balance[In](workerCount, waitForAllDownstreams = false))
      val merge = b.add(Merge[Out](workerCount))

      for (_ <- 1 to workerCount) {
        // for each worker, add an edge from the balancer to the worker, then wire it to the merge element
        balancer ~> worker.async ~> merge
      }

      FlowShape(balancer.in, merge.out)
    })
  }
}