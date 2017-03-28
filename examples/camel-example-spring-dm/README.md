# Camel Spring-DM Example

### Introduction

This example shows how use Spring DM (OSGi) with Camel. It can be run using Maven.

### Build
You will need to compile this example first:

	mvn install

### Run from cmd line outside OSGi container
To run the example using Maven type

	mvn camel:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Run inside OSGi container
You will need to compile and install this example first:

	mvn install

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell using this example's "features.xml" for easy provisioning.

	feature:repo-add camel ${version}
	feature:install camel
	feature:repo-add mvn:org.apache.camel/camel-example-spring-dm/${version}/xml/features
	feature:install camel-example-spring-dm

The example outputs to the log, which you can see using

	log:display

... or you can tail the log with

	log:tail

And use <kbd>ctrl</kbd>+<kbd>c</kbd> to break the tail.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
