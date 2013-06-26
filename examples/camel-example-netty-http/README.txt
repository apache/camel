Camel Netty HTTP Server Example
===============================

This example shows how to use a shared Netty HTTP Server in an OSGi environment.

There is 3 modules in this example

* shared-netty-http-server - The Shared Netty HTTP server that the other Camel applications uses.
* myapp-one - A Camel application that reuses the shared Netty HTTP server
* myapp-two - A Camel application that reuses the shared Netty HTTP server

You will need to compile and prepared this example first:
  mvn install

This example requires running in Apache Karaf / ServiceMix

To install Apache Camel in Karaf you type in the shell (we use version 2.12.0):

  features:chooseurl camel 2.12.0
  features:install camel
  
First you need to install the following features in Karaf/ServiceMix with:

  features:install camel-netty-http

Then you can install the shared Netty HTTP server which by default runs on port 8888.
The port number can be changed by editing the following source file:

  shared-netty-http-server/src/main/resources/OSGI-INF/blueprint/http-server.xml

In the Apache Karaf / ServiceMix shell type:

  osgi:install -s mvn:org.apache.camel/camel-example-netty-http-shared/2.12.0

Then you can install the Camel applications:

  osgi:install -s mvn:org.apache.camel/camel-example-netty-myapp-one/2.12.0
  osgi:install -s mvn:org.apache.camel/camel-example-netty-myapp-two/2.12.0

From a web browser you can then try the example by accessing the followign URLs:

  http://localhost:8888/one
  http://localhost:8888/two

This example is documented at
  http://camel.apache.org/netty-http-server-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
