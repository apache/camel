# Camel XML Example - CDI

### Introduction

This example illustrates the use of Camel XML configuration files into
Camel CDI applications.

While CDI favors a typesafe dependency injection mechanism, it may be useful
to reuse existing Camel XML configuration files into a Camel CDI application.
In other use cases, it might be handy to rely on the Camel XML DSL to configure
its Camel context(s).

So that example demonstrates how to import a Camel XML configuration file
and the level of interoperability between the Camel XML DSL and CDI.

It is implemented in Java with CDI dependency injection.
It uses JBoss Weld as the minimal CDI container to run the application,
though you can run the application in any CDI compliant container.

It is required to have the `camel-core-xml` dependency in the classpath
when importing Camel XML configuration files using Camel CDI.

The `camel-cdi` and `camel-stream` components are used in this example.
The `camel-test-cdi` module is used for the JUnit test runner.

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

When the Camel application runs, you should see the following messages
being logged to the console, e.g.:
```
2016-04-26 17:08:49,021 [cdi.Main.main()] INFO  Version                        - WELD-000900: 2.3.4 (Final)
2016-04-26 17:08:51,805 [cdi.Main.main()] INFO  CdiCamelExtension              - Camel CDI is starting Camel context [cdi-camel-xml]
2016-04-26 17:08:51,806 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: cdi-camel-xml) is starting
2016-04-26 17:08:52,322 [cdi.Main.main()] INFO  SedaEndpoint                   - Endpoint Endpoint[seda://rescue?multipleConsumers=true] is using shared queue: seda://rescue with size: 2147483647
2016-04-26 17:08:52,356 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: terminal started and consuming from: Endpoint[stream://in?delay=1000&promptMessage=Which+pill+%28red%7Cblue%29%3F%3A+]
2016-04-26 17:08:52,360 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: matrix started and consuming from: Endpoint[direct://neo]
2016-04-26 17:08:52,361 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: unplug started and consuming from: Endpoint[direct://unplug]
2016-04-26 17:08:52,362 [cdi.Main.main()] INFO  DefaultCamelContext            - Total 3 routes, of which 3 are started.
2016-04-26 17:08:52,363 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: cdi-camel-xml) started in 0.556 seconds
2016-04-26 17:08:52,434 [cdi.Main.main()] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container STATIC_INSTANCE initialized
████████╗██╗  ██╗███████╗    ███╗   ███╗ █████╗ ████████╗██████╗ ██╗██╗  ██╗
╚══██╔══╝██║  ██║██╔════╝    ████╗ ████║██╔══██╗╚══██╔══╝██╔══██╗██║╚██╗██╔╝
   ██║   ███████║█████╗      ██╔████╔██║███████║   ██║   ██████╔╝██║ ╚███╔╝ 
   ██║   ██╔══██║██╔══╝      ██║╚██╔╝██║██╔══██║   ██║   ██╔══██╗██║ ██╔██╗ 
   ██║   ██║  ██║███████╗    ██║ ╚═╝ ██║██║  ██║   ██║   ██║  ██║██║██╔╝ ██╗
   ╚═╝   ╚═╝  ╚═╝╚══════╝    ╚═╝     ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═╝╚═╝  ╚═╝
Which pill (red|blue)?: 

```

You're being prompted which pill Neo should be taking. If he chooses the red one,
Neo gets unplugged from the Matrix and the red pill serves as a location device
to track his position into the farm to broadcast it and have the rebellion
rescuing him, e.g.:
```
Which pill (red|blue)?: red
2016-04-26 17:12:07,520 [#1 - ControlBus] INFO  DefaultShutdownStrategy        - Starting to graceful shutdown 1 routes (timeout 300 seconds)
2016-04-26 17:12:07,521 [0 - stream://in] INFO  unplug                         - Neo has been located in matrix
2016-04-26 17:12:07,523 [ - ShutdownTask] INFO  DefaultShutdownStrategy        - Route: terminal shutdown complete, was consuming from: Endpoint[stream://in?delay=1000&promptMessage=Which+pill+%28red%7Cblue%29%3F%3A+]
2016-04-26 17:12:07,524 [ - ShutdownTask] INFO  DefaultShutdownStrategy        - Waiting as there are still 1 inflight and pending exchanges to complete, timeout in 300 seconds. Inflights per route: [terminal = 1]
2016-04-26 17:12:08,530 [#1 - ControlBus] INFO  DefaultShutdownStrategy        - Graceful shutdown of 1 routes completed in 1 seconds
                                     __ 
 _____         _                   _|  |
|  |  |___ ___| |_ _ ___ ___ ___ _| |  |
|  |  |   | . | | | | . | . | -_| . |__|
|_____|_|_|  _|_|___|_  |_  |___|___|__|
          |_|       |___|___|           
2016-04-26 17:12:08,532 [#1 - ControlBus] INFO  DefaultCamelContext            - Route: terminal is stopped, was consuming from: Endpoint[stream://in?delay=1000&promptMessage=Which+pill+%28red%7Cblue%29%3F%3A+]
2016-04-26 17:12:08,533 [#1 - ControlBus] INFO  ControlBusProducer             - ControlBus task done [stop route terminal] with result -> void
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell:
```
2016-04-26 17:16:50,172 [Thread-5       ] INFO  MainSupport$HangupInterceptor  - Received hang up - stopping the main instance.
2016-04-26 17:16:50,179 [Thread-5       ] INFO  CamelContextProducer           - Camel CDI is stopping Camel context [cdi-camel-xml]
2016-04-26 17:16:50,180 [Thread-5       ] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: cdi-camel-xml) is shutting down
2016-04-26 17:16:50,180 [Thread-5       ] INFO  DefaultShutdownStrategy        - Starting to graceful shutdown 3 routes (timeout 300 seconds)
2016-04-26 17:16:50,191 [ - ShutdownTask] INFO  DefaultShutdownStrategy        - Route: terminal shutdown complete, was consuming from: Endpoint[stream://in?delay=1000&promptMessage=Which+pill+%28red%7Cblue%29%3F%3A+]
2016-04-26 17:16:50,192 [ - ShutdownTask] INFO  DefaultShutdownStrategy        - Route: unplug shutdown complete, was consuming from: Endpoint[direct://unplug]
2016-04-26 17:16:50,192 [ - ShutdownTask] INFO  DefaultShutdownStrategy        - Route: matrix shutdown complete, was consuming from: Endpoint[direct://neo]
2016-04-26 17:16:50,192 [Thread-5       ] INFO  DefaultShutdownStrategy        - Graceful shutdown of 3 routes completed in 0 seconds
2016-04-26 17:16:50,236 [Thread-5       ] INFO  MainLifecycleStrategy          - CamelContext: cdi-camel-xml has been shutdown, triggering shutdown of the JVM.
2016-04-26 17:16:50,259 [Thread-5       ] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: cdi-camel-xml) uptime 7 minutes
2016-04-26 17:16:50,259 [Thread-5       ] INFO  DefaultCamelContext            - Apache Camel 2.18.0 (CamelContext: cdi-camel-xml) is shutdown in 0.079 seconds
 _____ _       _     _                     
|   __| |_ _ _| |_ _| |___ _ _ _ ___       
|__   |   | | |  _| . | . | | | |   |_ _ _ 
|_____|_|_|___|_| |___|___|_____|_|_|_|_|_|
2016-04-26 17:16:50,275 [Thread-5       ] INFO  Bootstrap                      - WELD-ENV-002001: Weld SE container STATIC_INSTANCE shut down
```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
