This repo contains my submission for a take-home exercise I received from Datadog.
Feel free to use anything you'd like.

Written below is what I wrote for them to read.

# An exercise in processing w3c access logs to collect metrics

I've included the built jar for ease of running, but there are also instructions for how to build.

I can't guarantee how this'll work on Windows, `*NIX` style paths are used in the code.

## Run

```
export LINES && java -jar datadog-assembly-0.1.jar --help
```

We need to run `export LINES` so that the console program will be able to access this environment
variable at runtime to determine the current number of available lines in the display.

```
java -jar datadog-assembly-0.1.jar --help
  -e, --exprimental                   Enable experimental features (last minute
                                      and un-tested, see README.) (default:
                                      false)
  -h, --hits-alert-threshold  <arg>   Alert if average rps is bigger than the
                                      given integer (default: 10)
  -l, --logfile  <arg>                Path to w3c formatted access log (default:
                                      /tmp/access.log
  -t, --test-run                      For development purposes, slows down
                                      reading the log. (default: false)
  -u, --update-interval  <arg>        Interval in seconds to update alerts,
                                      update display and rotate buckets
                                      (default: 10)
      --help                          Show help message
```

### Example

```
export LINES && java -jar datadog-assembly-0.1.jar \
  -l /var/log/access.log \
  -h 10 \
  -t true \
  -u 10
```

## Build (Mac)

Install Scala and sbt (and JDK..)
```
brew update
brew install scala
brew install sbt
```

Run:
```
sbt assembly
```

## Build (Docker)

The `sbt assembly` part from above is a pre-requisite, but because the .jar is included, then imagine we did it.

I basically whipped this up in the last minute, it worked on my mac but I couldn't try it in a linux environment.

Give it a shot, hopefully it'll work, otherwise go the sbt route.

However, there's a caveat: The console app reads the `LINES` environment variable to determine the number of lines in the terminal display.
This is used to place the output in the same location.

I could not get this working when running using `docker run`.
Something about the shell the process is running in is different than the shell from which I'm running docker.

```
docker build -t access-log-stream ./

```

Show help:
```
docker run access-log-stream --help
```

Run on default access log (`/tmp/access.log`)
```
docker run -v /tmp:/tmp access-log-stream
```

Run on sample file:

```
docker run -v `pwd`/src/main/resources:/tmp access-log-stream -l /tmp/full_access.log
```


## Implementation Details

The main entry point to the app is the `Main` class.
This main function:
- reads command line arguments to set up the application
- sets up the timer to read the log file every `n` seconds (will be referred to as `n`, default to 10)
- initialize stats buckets
- initializes the metrics pipeline
- initializes the continuous file reader
- registers a callback to run on `SIGINT` (stop pipeline and reader)
- starts the file reader

The file reader (`FollowingLineStream`) has a callback function associated with it.
It's built to call that function once with every line in the log file.

The callback function enqueues the line to the metrics pipeline.

The pipeline (`MetricsPipeline`) is built using an akka stream.
An akka stream is a computation graph describing a streaming process.
The graph is build like so:

```
                 / -> [parse line] -> [drop failures] -> [update current bucket]
                /  -> [parse line] -> [drop failures] -> [update current bucket]
[source queue] =   -> ...
                \  -> ...
                 \ -> [parse line] -> [drop failures] -> [update current bucket]
```

each call to update the current bucket is thread safe and can be done in parallel.

The `StatsBuckets` maintains a circular buffer of `StatBucket`, one for each `n` seconds.
A call to update the current bucket, increments the relevant stats based on the log record.

On each update every `n` seconds, we "commit" the current bucket and reset the oldest bucket to be the current one.

This means we can calculate interesting stats on the last `12*n` seconds in parallel to the current bucket getting streaming updates.

Upon instantiation, StatsBuckets creates AlertMonitors, a generic class that is created with a function to fetch a value
and a predicate function on whether or not the alert is breached. The alert monitor maintains a stack of alert messages.

On every commit of a bucket (every `n` seconds):
- alert monitors evaluate whether or not an alert fired
- interesting stats are calculated
- the console window is updated with information.

Note: One improvement I'd make is that right now the application is relying on stats calculation to take less than 1 second. (the minimum value for `n`)
This is a safe assumption in the current circumstances, but it's not ideal for a real world application.
Under "real world" circumstances, a different, distributed design would not have this problem.


If you were to review this code, I'd recommend the following order:
1. `AccessLogParser.scala`: Parse a w3c formatted log line to an object
2. `FollowingLineStream.scala`: Given a file, monitor it for contents and call `callback` on every line.
3. `AlertMonitor.scala`: Given a `getValue` function and a `predicate` function, monitor and maintain an alert state and messages.
4. `MetricsPipeline.scala`: The use of an akka stream to build a stream processing graph.
5. `StatsSummarizer.scala`: pretty print stuff
6. `StatsBuckets.scala`: implements the `process` functions that update stats in a time bucket.

## Notable things to note

### The use of timestamps in the log lines

I was a little confused about the requirements when it comes to the timing of log lines and triggering the hits alert.

On the one hand, each log line contains a timestamp, and if we wanted to assume the processing can happen in parallel and is scalable, then
we should assume that log lines do not get processed in order, and that any alerting on metrics that happened in a certain time window,
should use the timestamps in the log lines.

However, there was no guarantee that the log lines' timestamps would match the application time, and the exercise said

> Whenever total traffic for the past 2 minutes exceeds ...

That brought up the question of what is defined as "the last 2 minutes"?

If it's the last 2 minutes in application time, but log lines can describe events from any time, then
does the alert fire if the log line timestamps happens to fall within the application's time last 2 minutes?

I used my best judgement here and decided that since the log file is being read continuously and I process it in 
"real time" (down to 100ms read intervals), I consider the time I read each log line as the request time.
Basically this just means that I ignore the timestamp and if I process a line into a bucket, the request happened in the 
span of the current bucket.

If this was not the intent, then I think there's maybe a hidden assumption as to what the implementation 
would be and I'd love to explain my thinking there.

In any case, if I needed to take into account each line's timestamps, 
I'd have to potentially update multiple buckets in parallel, and every `n` seconds,
lock buckets to calculate stats and alerts and queue incoming events.

This did not seem very elegant and overly complex for a take-home exercise.

### Learning is fun!

I've never actually written a single computation graph using akka streams before.

I took this exercise as an opportunity to learn that library, I've read about it before
and thought it might be cool, so I did. I think it came out pretty cool.

I also have not yet had the opportunity to play around with Docker that much 
(a side effect of working at larger companies for a while..) so figuring out how to make a Dockerfile to package
up a Scala application was cool. Although it still requires one to have pre-built the .jar (which is why I've included it)

I bet there's a way to get `sbt` and invoke it as part of the Dockerfile.

### OS Compatibility

I developed on a mac, I'm not 100% sure how the console stuff (placing the contents in the same position)
is going to behave on different operating systems.
On my mac, the stats always get printed at the top of the screen which makes it look like it's a live display.
Normally, I'd find a library that does this better.

### Defaults

In the task description, there was ambiguity about the default log file path.
One line said

> It should default to reading /tmp/access.log and be overrideable

Another said:

> RUN touch /var/log/access.log  # since the program will read this by default

I chose the former.

### Experimental features

enable with `--exprimental true`, I added these for fun, I was just playing around at that point.

#### Efficient mode:

Most of the parsed log line is not used, `Efficient Mode` drops everything that's not needed.
The pipeline is written to work with a generic type.

This is not default to make it clear that the normal code validates the format of the entire log line.

#### Experimental SR alarm:

An `AlertMonitor` was written generically, I wanted to see if I could use the same implementation for different kinds of 
alerts. So I also added a success rate alert as an experimental feature.

## Improvements I'd make

In general, the design I chose here seems reasonable for the given requirements, specifically:
- calculating the stats is cheap and can be done incrementally (it's just counters)
- there are 10 whole seconds before rotating buckets, plenty of time to calculate stats while the current bucket is updating in parallel.

However, in the real world, I'd probably separate the functionalities here into different services:

1. An ingest pipeline that processes lines and emits count events (e.g. `(200, 1)`)
2. A streaming map reduce solution to compute stats values
3. an offline process that builds aggregated stats for larger buckets
4. a querying service that can query for metrics data based on different columns (e.g. `path/*/2xx`)
5. a monitoring service on top of the querying service that checks if alarms are breaching

### Other potential improvements

- Configurable bucket granularity and time range (currently 12 buckets is hard coded)
- Thread safety could be improved; The code assumes that the buckets span at least a second.
- could easily be extended to have the number of buckets more configurable
- more unit testing, better coverage.
- Make the continuous log file reading actually real time instead of a 100ms poll. Wasn't sure how to do that in Scala
  and it didn't seem that important for the purpose of exercise.


## Open Source used

- Twitter util-core for Time related classes and scala wrappers. (https://github.com/twitter/util/tree/develop/util-core)
- scallop - a simple Scala CLI parsing library (https://github.com/scallop/scallop)
- Akka (specifically Akka streams): A library to build computation graphs for stream processing on top of Akka (https://doc.akka.io/docs/akka/2.5/stream/)
- Scalatest / mockito: Unit testing libraries (https://github.com/scalatest/scalatest)
- `AccessLogParser` inspired by https://github.com/alvinj/ScalaApacheAccessLogParser/blob/master/src/main/scala/AccessLogParser.scala
