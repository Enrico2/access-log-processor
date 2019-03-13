package com.enricode.datadog

import java.io.{File, FileInputStream, InputStream}
import java.util.concurrent.atomic.AtomicBoolean
import scala.io.Source

object FollowingLineStream {
  def apply(filePath: String)(callback: String => Unit): FollowingLineStream = {
    new FollowingLineStream(new File(filePath))(callback)
  }

  /**
    * For use during development purposes. Essentially an artificially slow variation of FollowingLineStream
    */
  def testSlowStream(
    filePath: String,
    batchSize: Int,
    sleepBetweenBatch: Int
  )(
    callback: String => Unit
  ): FollowingLineStream = {
    require(batchSize > 0)

    var times = 0
    FollowingLineStream(filePath) { line =>
      callback(line)
      times = (times + 1) % batchSize
      if (times == 0) Thread.sleep(sleepBetweenBatch)
    }
  }
}

/**
  * Given a file and a function to perform on each line, read the contents of the file and invoke the
  * given function on each line. When reaching the file end, sleep for 100ms and try again.
  *
  * Inspired by https://github.com/alaz/tailf/blob/master/src/main/scala/com/osinka/tailf/Tail.scala
  */
class FollowingLineStream(file: File)(onLineRead: String => Unit) {
  require(file.exists(), s"File $file does not exist.")

  private[this] val stopped = new AtomicBoolean(false)

  private[this] val stream: InputStream = new InputStream {

    private[this] val underlying = new FileInputStream(file)

    override def read(): Int = follow(() => underlying.read())
    override def read(b: Array[Byte]): Int = read(b, 0, b.length)

    override def read(b: Array[Byte], off: Int, len: Int): Int = follow(() => underlying.read(b, off, len))

    private[this] def overflow = try {
      underlying.getChannel.position > file.length
    } catch {
      case _: Throwable => false
    }

    private[this] def isClosed = !underlying.getChannel.isOpen

    private[this] def follow(underlyingRead: () => Int): Int = {
      if (stopped.get()) {
        underlying.close()
        -1
      } else {
        underlyingRead() match {
          case -1 if overflow || isClosed => -1
          case -1 =>
            Thread.sleep(100)
            follow(underlyingRead)
          case i => i
        }
      }
    }
  }

  def stop(): Unit = stopped.set(true)

  def start(): Unit = Source.fromInputStream(stream).getLines().foreach(onLineRead)
}