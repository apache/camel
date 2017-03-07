# OpenTracing Example

### Introduction

This example shows how to use Camel with OpenTracing to trace all incoming and outgoing Camel messages.

The example uses a logging tracer (based on the MockTracer) to display tracing information on the console.

The example includes four sub maven modules that implement

- client
- service1
- service2
- loggingtracer

Where client -> service1 -> service2 using HTTP.

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

This service uses an annotation _CamelOpenTracing_ to indicate that the service should be traced.

When service1 is ready then start service2

```sh
$ cd service2
$ mvn compile exec:exec
```

This service is instrumented using an OpenTracing Java Agent, so the code does not need to be modified. The only requirement is that the OpenTracing Tracer and Camel OpenTracing component dependencies are added, and that the _opentracing-agent.jar_ Java Agent is specified when executing the service.

And then start the client that calls service1 every 30 seconds.

```sh
$ cd client
$ mvn compile camel:run
```

The client application explicitly instantiates and initializes the OpenTracing Tracer with the _CamelContext_.

The shells will show *SPAN FINISHED* messages indicating what spans have been reported from the client
and two services.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have. Enjoy!

The Camel riders!
