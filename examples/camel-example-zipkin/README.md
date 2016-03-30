# Zipkin Example

### Introduction

This example shows how to use Camel with Zipkin to trace/timing all incoming and outgoing Camel messages.

The example requires a running Zipkin Server.

The example includes two routes. The client route uses a timer to send a message to the server route.
The server route then replies back to the client.

### Configuration

The example is configured in the `src/main/resources/application.properties` file.

Here you need to configure the hostname and port number for the Zipkin Server.

### Build

You will need to compile this example first:

```sh
$ mvn compile
```

### Zipkin web console

You should be able to visualize the traces and timings from this example using the Zipkin Web Console.
The service name is named `hello`.


### Installing Zipkin Server using Docker

If you want to try Zipkin locally then you quickly try that using Docker.

There is a [quickstart guide at zipkin](http://zipkin.io/pages/quickstart.html) that has further instructions.
Remember to configure the IP address and port number in the `application.properties` file.

You can find the IP using `docker-machine ls`

### Run

To run the example, type:

```sh
$ mvn spring-boot:run
```

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have. Enjoy!

The Camel riders!
