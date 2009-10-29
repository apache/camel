Camel Route Throttling
======================

This example shows how to use the new feature in Camel 2.1 which is RoutePolicy.
A route policy allows you to associate a route with a policy. Camel provides a
throttling policy which allows Camel to dynamic throttle the route consumer
depending on the number of concurrent messages current in flight.

The example can be run using Maven.

The Server is required to be running when you start the client.
You can see on the server it should log in the console how it adjust the
throttling dynamically.

For the latest & greatest documentation on how to use this example please see:
  http://camel.apache.org/examples.html

You will need to compile this example first:
  mvn compile

The example should run if you type:
  mvn exec:java -PCamelServer

  mvn exec:java -PCamelClient

To stop the example hit ctrl + c

If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



