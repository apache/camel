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

    $ mvn install

### Run

#### Java SE

You can run this example using:

    $ mvn camel:run

When the Camel application starts, you should see the following message being logged to the console, e.g.:
```
2016-02-01 20:13:46,922 [cdi.Main.main()] INFO  DefaultCamelContext - Apache Camel 2.17-SNAPSHOT (CamelContext: osgi-example) started in 0.769 seconds
2016-02-01 20:13:47,008 [ Session Task-1] INFO  consumer-route      - Received message [Sample Message] from [Producer]
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

#### OSGi / Karaf

This example can be executed within Karaf. From the command line, in `bin` directory,
start Karaf:

    $ ./karaf
    
Then install the following pre-requisites:

    features:addUrl mvn:org.apache.camel.karaf/apache-camel/${version}/xml/features
    features:addUrl mvn:org.apache.activemq/activemq-karaf/5.12.1/xml/features
    features:install activemq-broker-noweb
    features:install pax-cdi-weld
    features:install camel-cdi
    features:install camel-sjms
    
Finally install and start the example:

    osgi:install -s mvn:org.apache.camel/camel-example-cdi-osgi/${version}

The following messages should be logged:

```
2016-02-01 20:28:43,446 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17-SNAPSHOT (CamelContext: osgi-example) is starting
2016-02-01 20:28:43,447 | INFO  | nsole user karaf | ManagedManagementStrategy        | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | JMX is enabled
2016-02-01 20:28:43,565 | INFO  | nsole user karaf | DefaultTypeConverter             | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Loaded 182 type converters
2016-02-01 20:28:43,585 | INFO  | nsole user karaf | DefaultRuntimeEndpointRegistry   | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
2016-02-01 20:28:43,675 | INFO  | nsole user karaf | BrokerService                    | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | Using Persistence Adapter: MemoryPersistenceAdapter
2016-02-01 20:28:43,679 | INFO  | nsole user karaf | BrokerService                    | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp.local-50823-1454354911797-0:2) is starting
2016-02-01 20:28:43,679 | INFO  | nsole user karaf | BrokerService                    | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | Apache ActiveMQ 5.12.1 (broker, ID:mbp.local-50823-1454354911797-0:2) started
2016-02-01 20:28:43,679 | INFO  | nsole user karaf | BrokerService                    | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | For help or more information please see: http://activemq.apache.org
2016-02-01 20:28:43,679 | WARN  | nsole user karaf | BrokerService                    | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | Memory Usage for the Broker (1024 mb) is more than the maximum available for the JVM: 455 mb - resetting to 70% of maximum available: 318 mb
2016-02-01 20:28:43,681 | INFO  | nsole user karaf | TransportConnector               | 187 - org.apache.activemq.activemq-osgi - 5.12.1 | Connector vm://broker started
2016-02-01 20:28:43,784 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | AllowUseOriginalMessage is enabled. If access to the original message is not needed, then its recommended to turn this option off as it may improve performance.
2016-02-01 20:28:43,784 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2016-02-01 20:28:43,845 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Route: consumer-route started and consuming from: Endpoint[sjms://sample.queue]
2016-02-01 20:28:43,845 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Total 1 routes, of which 1 is started.
2016-02-01 20:28:43,846 | INFO  | nsole user karaf | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Apache Camel 2.17-SNAPSHOT (CamelContext: osgi-example) started in 0.400 seconds
2016-02-01 20:28:43,899 | INFO  | Q Session Task-1 | consumer-route                   | 58 - org.apache.camel.camel-core - 2.17.0.SNAPSHOT | Received message [Sample Message] from [Producer]

```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
