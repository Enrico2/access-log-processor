package com.enricode.datadog

class StatsSummarizer {
  def summary(
    bucket: StatsBucket,
    alertMonitors: Option[AlertMonitor]*
  ): String = {
    val sb = StringBuilder.newBuilder
    sb.append("---------------------------- Stats ----------------------------\n")
    sb.append(bucket.statsString)
    sb.append("\n\n")

    alertMonitors.flatten.foreach { alertMonitor =>
      sb.append(alertMonitor.messages.mkString("\n"))
      sb.append("\n---\n")
    }

    val linesNeeded = 1 + sb.lines.length

    Option(System.getenv("LINES")).foreach { n =>
      sb.append("\n" * (n.toInt - linesNeeded))
    }

    sb.toString()
  }

  def bucketSummary(total: Int, errors: Int, counters: Map[String, Int]): String = {
    val sr = if (total > 0) (total - errors).toFloat * 100 / total else 100
    val general = Seq(
      "General stats" -> "",
      "Total requests" -> total.toString,
      "errors" -> errors.toString,
      "Success rate" -> s"$sr%",
    )

    val top10sections =
      counters
        .toSeq
        .sortBy { case (_, v) => -v }
        .take(10)
        .map { case (k, v) => (k, v.toString) }

    val top10 = Seq(
      "Top 10 visited" -> "",
      "section" -> "requests"
    ) ++ top10sections

    s"${tabulate(general)}\n\n${tabulate(top10)}"
  }

  /**
    * Print stuff in a table
    * +-----+-----+
    * |like |This |
    * +-----+-----+
    * |And  |This |
    * +-----+-----+
    */
  private[this] def tabulate(rows: Seq[(String, String)]): String = {
    def extend(s: String, length: Int) = s + (" " * (length - s.length))

    val (c1Size, c2Size) = rows.foldLeft((0, 0)) { case ((c1, c2), (k, v)) =>
      (math.max(c1, k.length), math.max(c2, v.toString.length))
    }

    val s1 = c1Size + 1
    val s2 = c2Size + 1

    val separatorRow = (("-" * s1) :: ("-" * s2) :: Nil).mkString("+", "+", "+")

    rows.flatMap { case (k, v) =>
      if (v.isEmpty) {
        Seq(
          separatorRow,
          s"|${extend(k, s1 + s2 + 1)}|",
          separatorRow
        )
      } else {
        Seq(
          (extend(k, s1) :: extend(v.toString, s2) :: Nil).mkString("|", "|", "|"),
          separatorRow
        )
      }
    }.mkString("\n")
  }
}
