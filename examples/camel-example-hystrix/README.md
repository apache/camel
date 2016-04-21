# Hystrix Example

### Introduction

This example shows how to use Camel with Hystrix EIP as circuit breaker in Camel routes

The example includes three sub maven modules that implement

- client
- service1
- service2

Where client -> service1
      client -> service2 (fallback)

### Configuration

Service1 is configured in the `src/main/java/sample/camel/Service1Application.java` source code.
Service2 is configured in the `src/main/resources/application.properties` properties file.

### Build

You will need to compile this example first:

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

And then start the client that calls service1 every 10th seconds.

```sh
$ cd client
$ mvn compile camel:run
```

You can then stop service1 and see that the client should fallback to service2 in the Hystrix EIP circuit breaker.

### Hystrix web console

You should be able to visualize the state of the Hystrix Circuit Breaker in the Hystrix Web Console.

You can find instructions at Hystrix how to build and run the web console: https://github.com/Netflix/Hystrix/wiki/Dashboard

For example using gradle, you can then access the web console locally at: `http://localhost:7979/hystrix-dashboard`.

The stream is accessinble from the client at: `http://localhost:8080/hystrix.stream` which you can add as stream
to the web console and then you should see the circuit breakers.

![Hystrix Web Console](images/hystrix-web-console.png "Hystrix Web Console")

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have. Enjoy!

The Camel riders!
