# Zipkin Example

### Introduction

This example shows how to use Camel with Zipkin to trace/timing all incoming and outgoing Camel messages.

The example requires a running Zipkin Server.

The example includes three sub maven modules that implement

- client
- service1
- service2

Where client -> service1 -> service2 using HTTP.

### Configuration

This example assumes you will run Zipkin on the same host using the default collector port of 9410.  If you wish to change those, you can do so using these files:  

Service1 is configured in the `src/main/resources/application.properties` properties file.
Service2 is configured in the `src/main/java/sample/camel/Service2Route.java` source file.
Client is configured in the `src/main/java/sample/camel/ClientApplication.java` source file.

### Build

First, start Zipkin as described below in the [Installing Zipkin Server]("Installing Zipkin Server") section

Then compile this example:

```sh
$ mvn compile
```

### Run the example

Then using three different shells and run service1 and service2 before the client.

```sh
$ cd service1
$ mvn compile spring-boot:run
```

When service1 is ready then start service2

```sh
$ cd service2
$ mvn compile camel:run
```

And then start the client that calls service1 every 30th seconds.

```sh
$ cd client
$ mvn compile camel:run
```

### Zipkin UI

You should be able to visualize the traces and timings from this example using the Zipkin UI.
The services are named `service1` and `service2`.

In the screen shot below we are showing a trace of a client calling service1 and service2.

![Zipkin UI Trace Details](images/zipkin-web-console-1.png "Detail of a trace")

You can then click on each span and get annotated data from the Camel exchange and about the requests as shown:

![Zipkin UI Span Details](images/zipkin-web-console-2.png "Detail of the span")


### Installing Zipkin Server 

The quickest way to get Zipkin started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar.

```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
```

.. and then run it

```bash
java -jar zipkin.jar
```

Finally, browse to http://localhost:9411 to find traces!

### Installing Zipkin Server using Docker

If you want to try Zipkin locally then you quickly try that using Docker.

There is a [quickstart guide at zipkin](http://zipkin.io/pages/quickstart.html) that has further instructions.
Remember to configure the IP address and port number in the `application.properties` file.

You can find the IP using `docker-machine ls`

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have. Enjoy!

The Camel riders!
