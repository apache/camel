# Metrics Example - CDI

### Introduction

This example illustrates the integration between Camel, Dropwizard Metrics and CDI.

The example emulates an unreliable service that processes a continuous stream of events.
The unreliable service fails randomly and metrics get collected to report
the number of generated events as well as the total number of attempted, redelivered,
failed and successful calls to the service. A SLF4J reporter is configured so that
these metrics details get logged every 10 seconds in the console.

The example is implemented in Java with CDI dependency injection. It uses JBoss Weld
as the minimal CDI container to run the application, though you can run the application
in any CDI compliant container.

The `camel-cdi` and `camel-metrics` components are used in this example.
The `camel-test-cdi` module is used for the JUnit test runner.
Besides, the Metrics CDI extension is used so that metrics can be injected
and custom metrics registered via CDI. For instance, a ratio gauge is registered
to monitor the success rate, that is the ratio of success calls on the number
of generated events.

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ mvn camel:run
```

When the Camel application runs, you should see the calls to the 'unreliable-service' being logged to the console, e.g.:
```
2016-01-18 15:19:04,390 [ timer://stream] INFO  unreliable-service - Processing event #8...
2016-01-18 15:19:06,399 [ timer://stream] ERROR unreliable-service - Failed processing event #8
2016-01-18 15:19:06,400 [ timer://stream] INFO  unreliable-service - Processing event #9...
2016-01-18 15:19:08,410 [ timer://stream] ERROR unreliable-service - Failed processing event #9
2016-01-18 15:19:08,411 [ timer://stream] INFO  unreliable-service - Processing event #10...
2016-01-18 15:19:08,412 [ timer://stream] INFO  unreliable-service - Successfully processed event #10
2016-01-18 15:19:09,415 [ timer://stream] INFO  unreliable-service - Processing event #11...
2016-01-18 15:19:09,416 [ timer://stream] INFO  unreliable-service - Successfully processed event #11
2016-01-18 15:19:10,420 [ timer://stream] INFO  unreliable-service - Processing event #12...
2016-01-18 15:19:10,421 [ timer://stream] INFO  unreliable-service - Successfully processed event #12
2016-01-18 15:19:11,424 [ timer://stream] INFO  unreliable-service - Processing event #13...
2016-01-18 15:19:12,428 [ timer://stream] WARN  unreliable-service - Processed event #13 after 1 retries
2016-01-18 15:19:12,430 [ timer://stream] INFO  unreliable-service - Successfully processed event #13
```

And every 10 seconds, the metrics report, e.g.:
```
2016-01-18 15:19:14,360 [rter-1-thread-1] INFO  metrics - type=GAUGE, name=success-ratio, value=0.9314661799835947
2016-01-18 15:19:14,361 [rter-1-thread-1] INFO  metrics - type=METER, name=attempt, count=26, mean_rate=1.3682531895692165, m1=1.245416192969619, m5=1.209807850571521, m15=1.2033118138834105, rate_unit=events/second
2016-01-18 15:19:14,361 [rter-1-thread-1] INFO  metrics - type=METER, name=error, count=2, mean_rate=0.25121358141009453, m1=0.4, m5=0.4, m15=0.4, rate_unit=events/second
2016-01-18 15:19:14,361 [rter-1-thread-1] INFO  metrics - type=METER, name=generated, count=15, mean_rate=0.7210025396112787, m1=0.6455184225121126, m5=0.6098087536676114, m15=0.6033118478925024, rate_unit=events/second
2016-01-18 15:19:14,361 [rter-1-thread-1] INFO  metrics - type=METER, name=redelivery, count=11, mean_rate=0.6872842357052532, m1=0.9385926899562456, m5=0.9868864401928024, m15=0.995580155717569, rate_unit=events/second
2016-01-18 15:19:14,361 [rter-1-thread-1] INFO  metrics - type=METER, name=success, count=12, mean_rate=0.5768131773739456, m1=0.6012785791263936, m5=0.6000546385677541, m15=0.6000061386568257, rate_unit=events/second
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
