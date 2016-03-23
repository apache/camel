# Camel Route Throttling

### Introduction

This example shows how to use the new feature in Camel 2.1 which is RoutePolicy.
A route policy allows you to associate a route with a policy. Camel provides a
throttling policy which allows Camel to dynamic throttle the route consumer
depending on the number of concurrent messages current in flight.

The Server is required to be running when you start the client.
You can see on the server it should log in the console how it adjust the
throttling dynamically.

The goal of this example is to illustrate that Camel throttles the JMS queue
to be on same pace with the rest of the Camel routing. When running the example
you should observe that the JMS route and the SEDA route completes nearly in sync.

### Build

You will need to compile this example first:

	mvn compile

### Run

The example should run if you type:

	mvn exec:java -PCamelServer


	mvn exec:java -PCamelClient


	mvn exec:java -PCamelFileClient

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Documentation

This example is documented at
  <http://camel.apache.org/route-throttling-example.html>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
