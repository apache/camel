# Camel and RMI

### Introduction

This example shows how to work with the Camel-RMI Component.

The example exposes a RMI service over port `37541` running as a Camel application.
The Camel application must be running.

The Client is standalone and run in a separate JVM. It invokes the RMI service and outputs the reply.

### Build

You will need to compile this example first:

	mvn install

### Run from cmd line outside OSGi container

To start the server

	mvn camel:run

To run the client

	mvn exec:java -PClient

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>


### Run inside OSGi container

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell

First the camel-rmi feature must be installed

	feature:repo-add camel ${version}
	feature:install camel-spring-dm
	feature:install camel-rmi

Then install the example

	install -s mvn:org.apache.camel/camel-example-osgi-rmi/${version}

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
