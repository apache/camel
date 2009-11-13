Camel HTTP Async
================

This example shows how to use the new feature in Camel 2.1 which is support
for non blocking asynchronous producers.

Currently camel-jetty implements this to the fullest as its JettyHttpProducer
supports non blocking request/reply natively in Jetty.

This example shows a client and a server in action. The client sends 100 messages
to the server over HTTP which the server processes and returns a reply.

The client is working using a single threaded to route the messages to the point
where they are send to the HTTP server. As we use non blocking asynchronous
request/reply this single thread will terminate its current task and be ready
immediately to route the next message. This allows us to have higher throughput
as the single thread can go as fast as it can, it does not have to wait for
the HTTP server to reply (i.e. its not blocking).

You can see the difference if you change the async=true option to async=false
in the src/main/resources/META-INF/spring/camel-client.xml file.


For the latest & greatest documentation on how to use this example please see:
  http://camel.apache.org/route-throttling-example.html

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



