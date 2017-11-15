# Camel Netty HTTP Server Example

### Introduction

This example shows how to use a shared Netty HTTP Server in an OSGi environment.

There is 4 modules in this example:

* `shared-netty-http-server` - The Shared Netty HTTP server that the other Camel applications uses
* `myapp-one` - A Camel application that reuses the shared Netty HTTP server
* `myapp-two` - A Camel application that reuses the shared Netty HTTP server

### Build

You will need to compile this example first:

```sh
$ mvn install
```

### Run

This example runs in Apache Karaf / ServiceMix. To install Apache Camel in Karaf you type in the shell:

```sh
karaf@root()> repo-add camel ${version}
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
karaf@root()> install -s mvn:org.apache.camel.example/camel-example-netty-http-shared/${version}
```

Then you can install the Camel applications:

```sh
karaf@root()> install -s mvn:org.apache.camel.example/camel-example-netty-myapp-one/${version}
karaf@root()> install -s mvn:org.apache.camel.example/camel-example-netty-myapp-two/${version}
```

From a web browser you can then try the example by accessing the following URLs:

<http://localhost:8888/one>

<http://localhost:8888/two>

Camel commands can be used to gain some insights on the CDI Camel
context, e.g.:

- The `camel:context-list` displays the CDI Camel contexts:

    ```
    karaf@root()> camel:context-list
     Context           Status              Total #       Failed #     Inflight #   Uptime        
     -------           ------              -------       --------     ----------   ------        
     camel-1           Started                   1              0              0   1 minute  
     camel-2           Started                   1              0              0   1 minute  
    ```

Or by tailing the log with:

```sh
karaf@root()> log:tail
```

The following messages should be displayed:

```
2017-05-10 21:36:55,582 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | Attempting to start Camel Context camel-2
2017-05-10 21:36:55,582 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | Apache Camel 2.17.0 (CamelContext: camel-2) is starting
2017-05-10 21:36:55,583 | INFO  | nt Dispatcher: 1 | ManagedManagementStrategy        | 54 - org.apache.camel.camel-core - 2.17.0 | JMX is enabled
2017-05-10 21:36:55,624 | INFO  | nt Dispatcher: 1 | DefaultRuntimeEndpointRegistry   | 54 - org.apache.camel.camel-core - 2.17.0 | Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: 1000)
2017-05-10 21:36:55,638 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
2017-05-10 21:36:55,639 | INFO  | nt Dispatcher: 1 | NettyHttpEndpoint                | 60 - org.apache.camel.camel-netty-http - 2.17.0 | NettyHttpConsumer: Consumer[http://localhost/two] is using NettySharedHttpServer on port: 8888
2017-05-10 21:36:55,646 | INFO  | nt Dispatcher: 1 | NettyConsumer                    | 59 - org.apache.camel.camel-netty - 2.17.0 | Netty consumer bound to: localhost:8888
2017-05-10 21:36:55,646 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | Route: http-route-two started and consuming from: http://localhost/two
2017-05-10 21:36:55,647 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | Total 1 routes, of which 1 are started.
2017-05-10 21:36:55,647 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 52 - org.apache.camel.camel-blueprint - 2.17.0 | Apache Camel 2.17.0 (CamelContext: camel-2) started in 0.065 seconds
```

Hit <kbd>ctrl</kbd>+<kbd>c</kbd> to exit the log command.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
