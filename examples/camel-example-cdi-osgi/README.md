# OSGi Example - CDI

### Introduction

This example illustrates a CDI application that can be executed inside an OSGi container
using PAX CDI. This application can run unchanged as well in Java SE inside a standalone
CDI container.

The example starts an ActiveMQ in-memory broker and publishes a message when the Camel
context has started.

The example is implemented in Java with CDI dependency injection. It uses JBoss Weld
as the minimal CDI container to run the application, though you can run the application
in any CDI compliant container. In OSGi, PAX CDI is used to managed the lifecycle of
the CDI container.

The `camel-core` and `camel-sjms` components are used in this example.

### Build

You will need to build this example first:

```sh
$ mvn install
```

### Run

#### Java SE

You can run this example using:

```sh
$ mvn camel:run
```

When the Camel application starts, you should see the following message being logged to the console, e.g.:

```
2019-06-30 22:31:28,826 [cdi.Main.main()] INFO  Version                        - WELD-000900: 3.0.5 (Final)
2019-06-30 22:31:29,152 [cdi.Main.main()] INFO  Bootstrap                      - WELD-000101: Transactional services not available. Injection of @Inject UserTransaction not available. Transactional observers will be invoked synchronously.
2019-06-30 22:31:29,337 [cdi.Main.main()] INFO  Event                          - WELD-000411: Observer method [BackedAnnotatedMethod] private org.apache.camel.cdi.CdiCamelExtension.processAnnotatedType(@Observes ProcessAnnotatedType<?>) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds.
2019-06-30 22:31:30,010 [cdi.Main.main()] INFO  CdiCamelExtension              - Camel CDI is starting Camel context [osgi-example]
2019-06-30 22:31:30,016 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 3.0.0 (CamelContext: osgi-example) is starting
2019-06-30 22:31:30,016 [cdi.Main.main()] INFO  DefaultCamelContext            - Tracing is enabled on CamelContext: osgi-example
2019-06-30 22:31:30,017 [cdi.Main.main()] INFO  DefaultManagementStrategy      - JMX is disabled
2019-06-30 22:31:30,367 [cdi.Main.main()] INFO  DefaultCamelContext            - StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2019-06-30 22:31:30,434 [cdi.Main.main()] INFO  BrokerService                  - Using Persistence Adapter: MemoryPersistenceAdapter
2019-06-30 22:31:30,594 [cdi.Main.main()] INFO  BrokerService                  - Apache ActiveMQ 5.15.9 (broker, ID:Babaks-iMac-50846-1561926690454-0:1) is starting
2019-06-30 22:31:30,599 [cdi.Main.main()] INFO  BrokerService                  - Apache ActiveMQ 5.15.9 (broker, ID:Babaks-iMac-50846-1561926690454-0:1) started
2019-06-30 22:31:30,599 [cdi.Main.main()] INFO  BrokerService                  - For help or more information please see: http://activemq.apache.org
2019-06-30 22:31:30,626 [cdi.Main.main()] INFO  TransportConnector             - Connector vm://broker started
2019-06-30 22:31:30,711 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: consumer-route started and consuming from: sjms://sample.queue
2019-06-30 22:31:30,713 [cdi.Main.main()] INFO  DefaultCamelContext            - Total 1 routes, of which 1 are started
2019-06-30 22:31:30,714 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 3.0.0 (CamelContext: osgi-example) started in 0.701 seconds
2019-06-30 22:31:30,779 [ Session Task-1] INFO  consumer-route                 - Received message [Sample Message] from [Producer]
2019-06-30 22:31:30,785 [cdi.Main.main()] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container 6dd6ff85-2756-49eb-ba0e-50d7feece6b6 initialized
2019-06-30 22:31:30,792 [cdi.Main.main()] INFO  MainSupport                    - Using properties from classpath:application.properties
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

#### OSGi / Karaf

This example can be executed within Karaf 4.x. Note that it uses
the `pax-cdi-weld` feature from the PAX CDI version used by Camel and which defines
the Weld version used. For instance, Camel `${version}` depends on PAX CDI `${pax-cdi-version}`
and related `pax-cdi-weld` feature.

To run the example, from the command line:

1. In the Karaf install root directory, start Karaf:

    ```sh
    $ bin/karaf
    ```

2. Install the pre-requisites:

    ```sh
    karaf@root()> repo-add camel ${version}
    karaf@root()> repo-add activemq ${activemq-version}
    karaf@root()> feature:install activemq-broker-noweb pax-cdi-weld camel-sjms camel-cdi
    ```

3. Then install and start the example:

    ```sh
    karaf@root()> install -s mvn:org.apache.camel.example/camel-example-cdi-osgi/${version}
    ```

By tailing the log with:

```sh
karaf@root()> log:tail
```

The following messages should be displayed:

```
2016-02-08 12:32:14,395 | INFO  | nsole user karaf | CdiCamelExtension                | 149 - org.apache.camel.camel-cdi - 2.17.0.SNAPSHOT | Camel CDI is starting Camel context [osgi-example]
2016-02-08 12:32:14,395 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17.0 (CamelContext: osgi-example) is starting
2016-02-08 12:32:14,395 | INFO  | nsole user karaf | ManagedManagementStrategy        | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | JMX is enabled
2016-02-08 12:32:14,698 | INFO  | nsole user karaf | DefaultTypeConverter             | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Loaded 182 type converters
2016-02-08 12:32:14,706 | INFO  | nsole user karaf | DefaultRuntimeEndpointRegistry   | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
2016-02-08 12:32:14,730 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Using Persistence Adapter: MemoryPersistenceAdapter
2016-02-08 12:32:14,731 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp-2.local-52027-1454930701800-0:3) is starting
2016-02-08 12:32:14,731 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp-2.local-52027-1454930701800-0:3) started
2016-02-08 12:32:14,731 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | For help or more information please see: http://activemq.apache.org
2016-02-08 12:32:14,731 | WARN  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Memory Usage for the Broker (1024 mb) is more than the maximum available for the JVM: 455 mb - resetting to 70% of maximum available: 318 mb
2016-02-08 12:32:14,732 | INFO  | nsole user karaf | TransportConnector               | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Connector vm://broker started
2016-02-08 12:32:14,755 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | AllowUseOriginalMessage is enabled. If access to the original message is not needed, then its recommended to turn this option off as it may improve performance.
2016-02-08 12:32:14,755 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2016-02-08 12:32:14,762 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Route: consumer-route started and consuming from: Endpoint[sjms://sample.queue]
2016-02-08 12:32:14,763 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Total 1 routes, of which 1 is started.
2016-02-08 12:32:14,763 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17.0 (CamelContext: osgi-example) started in 0.368 seconds
2016-02-08 12:32:14,774 | INFO  | Q Session Task-1 | consumer-route                   | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Received message [Sample Message] from [Producer]
```

Hit <kbd>ctrl</kbd>+<kbd>c</kbd> to exit the log command.

Camel commands can be used to gain some insights on the CDI Camel
context, e.g.:

- The `camel:context-list` displays the CDI Camel context:

    ```
    karaf@root()> camel:context-list
     Context        Status              Total #       Failed #     Inflight #   Uptime        
     -------        ------              -------       --------     ----------   ------        
     osgi-example   Started                   1              0              0   1 minute  
    ```

- The `camel:route-list` command displays the Camel route configured by the `RouteBuilder` bean:

    ```
    karaf@root()> camel:route-list
     Context        Route            Status              Total #       Failed #     Inflight #   Uptime     
     -------        -----            ------              -------       --------     ----------   ------      
     osgi-example   consumer-route   Started                   1              0              0   3 minutes
    ```

- And the `camel:route-info` command displays the exchange completed
  when the `CamelContextStartedEvent` CDI event is fired:

    ```
    karaf@root()> camel:route-info consumer-route
    Camel Route consumer-route
       Camel Context: osgi-example
       State: Started
       State: Started

    Statistics
       Exchanges Total: 1
       Exchanges Completed: 1
       Exchanges Failed: 0
       Exchanges Inflight: 0
       Min Processing Time: 1 ms
       Max Processing Time: 1 ms
       Mean Processing Time: 1 ms
       Total Processing Time: 1 ms
       Last Processing Time: 1 ms
       Delta Processing Time: 1 ms
       Start Statistics Date: 2016-02-08 12:32:14
       Reset Statistics Date: 2016-02-08 12:32:14
       First Exchange Date: 2016-02-08 12:32:14
       Last Exchange Date: 2016-02-08 12:32:14
    ```

Finally, you can stop the example with:

```sh
karaf@root()> uninstall camel-example-cdi-osgi
```

And check in the log that the Camel context has been gracefully
shutdown:

```
2016-02-08 12:39:34,295 | INFO  | nsole user karaf | CamelContextProducer             | 149 - org.apache.camel.camel-cdi - 2.17.0.SNAPSHOT | Camel CDI is stopping Camel context [osgi-example]
2016-02-08 12:39:34,295 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17.0 (CamelContext: osgi-example) is shutting down
2016-02-08 12:39:34,297 | INFO  | nsole user karaf | DefaultShutdownStrategy          | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Starting to graceful shutdown 1 routes (timeout 300 seconds)
2016-02-08 12:39:34,299 | INFO  | 1 - ShutdownTask | DefaultShutdownStrategy          | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Route: consumer-route shutdown complete, was consuming from: Endpoint[sjms://sample.queue]
2016-02-08 12:39:34,300 | INFO  | nsole user karaf | DefaultShutdownStrategy          | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Graceful shutdown of 1 routes completed in 0 seconds
2016-02-08 12:39:34,310 | INFO  | nsole user karaf | TransportConnector               | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Connector vm://broker stopped
2016-02-08 12:39:34,310 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp-2.local-52027-1454930701800-0:3) is shutting down
2016-02-08 12:39:34,311 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp-2.local-52027-1454930701800-0:3) uptime 7 minutes
2016-02-08 12:39:34,311 | INFO  | nsole user karaf | BrokerService                    | 61 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp-2.local-52027-1454930701800-0:3) is shutdown
2016-02-08 12:39:34,313 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17.0 (CamelContext: osgi-example) uptime 7 minutes
2016-02-08 12:39:34,313 | INFO  | nsole user karaf | DefaultCamelContext              | 151 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17.0 (CamelContext: osgi-example) is shutdown in 0.018 seconds
```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
