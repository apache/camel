Camel and RMI
=============

This example shows how to work with the Camel-RMI Component.

The example exposes a RMI service over port 37541 running as a Camel application.
The Camel application must be running.

The Client is standalone and run in a separate JVM. It invokes the RMI service and outputs the reply.

You will need to compile this example first:
  mvn compile

To start the server
  mvn camel:run

To run the client
  mvn exec:java -PClient

To stop the example hit ctrl + c

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell

First the camel-rmi feature must be installed

  features:install camel-rmi

Then install the example

  osgi:install mvn:org.apache.camel/camel-example-osgi-rmi/2.8.0

      (substitute 2.8.0 with the Camel version number)

Then start the bundle by starting the id it was assigned during installation

  osgi:start 182

      (substitute 182 with the id of the bundle)


If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
