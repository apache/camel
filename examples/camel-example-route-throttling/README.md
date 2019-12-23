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

### How to Run

The example has 3 maven goals to run the example

    mvn compile exec:java -PCamelServer
    
  Starts the Camel Server which contains the 3 routes and where you should check its log output for how it goes.

    mvn compile exec:java -PCamelClient
    
  Is a client that sends 10000 JMS messages to the JMS broker which is consumed by route1. The Server must be started beforehand.

    mvn compile exec:java -PCamelFileClient
    
  Is a client that creates 5000 files that are consumed by route2. The server may be started beforehand, but its not required.

So at first you start the server. Then at any time you can run a client at will. For example you can run the JMS client and let it run to completion at the server. You can see at the server console logging that it reports the progress. And at sometime it will reach 10000 messages processed. You can then start the client again if you like.
You can also start the other client to create the files which then let the example be a bit more complicated as we have concurrent processing of JMS messages and files at the same time. And where as both of these should be dynamic throttled so we wont go too fast.

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Help and contributions

If you hit any problem using Camel or have some feedback, 
then please [let us know](https://camel.apache.org/support.html).

We also love contributors, 
so [get involved](https://camel.apache.org/contributing.html) :-)

The Camel riders!
