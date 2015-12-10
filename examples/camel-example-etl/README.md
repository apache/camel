# Extract Transform Load (ETL) Example

### Introduction

This example shows how to use Camel as an ETL tool

### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example type

	mvn camel:run

You can see the routing rules by looking at the java code in the src/main/java
directory and the Spring XML configuration lives in
  `src/main/resources/META-INF/spring`

To stop the example hit `ctrl + c`

### Build and Run inside OSGi container


#### Build

You will need to compile and install this example first:

	mvn install

#### Install

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell using this example's "features.xml" for easy provisioning.

	features:addUrl mvn:org.apache.camel/camel-example-etl/${version}/xml/features
	features:install camel-example-etl

The example outputs logs into the console. When you're done just hit `ctrl + d` to exit the container.
Next time you start the container again use the 'clean' option so that this example's bundle gets
removed and you don't see the logs anymore written into the console, e.g. in case of Karaf start it
again using:

	karaf clean

### Documentation

For a full description of this example please see
  <http://camel.apache.org/etl-example.html>

<http://camel.apache.org/etl.html>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
