# Camel Netty HTTP Server Example

### Introduction

This example shows how to use a shared Netty HTTP Server in an OSGi environment.

There is 4 modules in this example:

* `shared-netty-http-server` - The Shared Netty HTTP server that the other Camel applications uses
* `myapp-one` - A Camel application that reuses the shared Netty HTTP server
* `myapp-two` - A Camel application that reuses the shared Netty HTTP server
* `myapp-cdi` - A Camel CDI application that reuses the shared Netty HTTP server

### Build

You will need to compile this example first:

```sh
$ mvn install
```

### Run

This example runs in Apache Karaf / ServiceMix. To install Apache Camel in Karaf you type in the shell:

```sh
karaf@root()> repo-add camel 2.17.0
karaf@root()> feature:install camel
```

Then you need to install the following features in Karaf/ServiceMix:

```sh
karaf@root()> feature:install camel-netty-http
```

Then you can install the shared Netty HTTP server which by default runs on port `8888`.
The port number can be changed by editing the following source file:

  `shared-netty-http-server/src/main/resources/OSGI-INF/blueprint/http-server.xml`

In the Apache Karaf / ServiceMix shell type:

```sh
karaf@root()> install -s mvn:org.apache.camel/camel-example-netty-http-shared/2.17.0
```

Then you can install the Camel applications:

```sh
karaf@root()> install -s mvn:org.apache.camel/camel-example-netty-myapp-one/2.17.0
karaf@root()> install -s mvn:org.apache.camel/camel-example-netty-myapp-two/2.17.0
```

If you want to test the Camel CDI application, you first need to install the required features:

```sh
karaf@root()> feature:install pax-cdi-weld camel-cdi
```

And then install the Camel CDI application:

```sh
karaf@root()> install -s mvn:org.apache.camel/camel-example-netty-myapp-cdi/2.17.0
```

From a web browser you can then try the example by accessing the followign URLs:

<http://localhost:8888/one>

<http://localhost:8888/two>

<http://localhost:8888/cdi>

Camel commands can be used to gain some insights on the CDI Camel
context, e.g.:

- The `camel:context-list` displays the CDI Camel contexts:

    ```
    karaf@root()> camel:context-list
     Context           Status              Total #       Failed #     Inflight #   Uptime        
     -------           ------              -------       --------     ----------   ------        
     camel-1           Started                   1              0              0   1 minute  
     netty-myapp-cdi   Started                   1              0              0   1 minute  
    ```

Or by tailing the log with:

```sh
karaf@root()> log:tail
```

The following messages should be displayed:

```
22016-05-06 15:38:31,340 | INFO  | l Console Thread | CdiExtender                      | 83 - org.ops4j.pax.cdi.extender - 1.0.0.RC1 | creating CDI container for bean bundle org.apache.camel.camel-example-netty-myapp-cdi [97] with extension bundles [org.ops4j.pax.cdi.extension [82], org.apache.camel.camel-cdi [92]]
 2016-05-06 15:38:31,340 | INFO  | l Console Thread | AbstractCdiContainer             | 81 - org.ops4j.pax.cdi.spi - 1.0.0.RC1 | Starting CDI container for bundle org.apache.camel.camel-example-netty-myapp-cdi [97]
 2016-05-06 15:38:31,595 | INFO  | l Console Thread | CdiCamelExtension                | 92 - org.apache.camel.camel-cdi - 2.17.0 | Camel CDI is starting Camel context [netty-myapp-cdi]
 2016-05-06 15:38:31,595 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | Apache Camel 2.17.0 (CamelContext: netty-myapp-cdi) is starting
 2016-05-06 15:38:31,595 | INFO  | l Console Thread | ManagedManagementStrategy        | 58 - org.apache.camel.camel-core - 2.17.0 | JMX is enabled
 2016-05-06 15:38:31,612 | INFO  | l Console Thread | DefaultTypeConverter             | 58 - org.apache.camel.camel-core - 2.17.0 | Loaded 182 type converters
 2016-05-06 15:38:31,621 | INFO  | l Console Thread | DefaultRuntimeEndpointRegistry   | 58 - org.apache.camel.camel-core - 2.17.0 | Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
 2016-05-06 15:38:31,639 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | AllowUseOriginalMessage is enabled. If access to the original message is not needed, then its recommended to turn this option off as it may improve performance.
 2016-05-06 15:38:31,639 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
 2016-05-06 15:38:31,639 | INFO  | l Console Thread | NettyHttpEndpoint                | 66 - org.apache.camel.camel-netty-http - 2.17.0 | NettyHttpConsumer: Consumer[http://localhost/cdi] is using NettySharedHttpServer on port: 8888
 2016-05-06 15:38:31,649 | INFO  | l Console Thread | NettyConsumer                    | 65 - org.apache.camel.camel-netty - 2.17.0 | Netty consumer bound to: localhost:8888
 2016-05-06 15:38:31,649 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | Route: http-route-cdi started and consuming from: Endpoint[http://localhost/cdi]
 2016-05-06 15:38:31,650 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | Total 1 routes, of which 1 are started.
 2016-05-06 15:38:31,650 | INFO  | l Console Thread | DefaultCamelContext              | 58 - org.apache.camel.camel-core - 2.17.0 | Apache Camel 2.17.0 (CamelContext: netty-myapp-cdi) started in 0.055 seconds
```

Hit <kbd>ctrl</kbd>+<kbd>c</kbd> to exit the log command.

This example is documented at
<http://camel.apache.org/netty-http-server-example.html>

### Documentation

This example is documented at
<http://camel.apache.org/netty-http-server-example.htmll>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
