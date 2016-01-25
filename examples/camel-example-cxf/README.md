# Camel CXF Example

### Introduction

This example shows how to work with CXF and Camel.

### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example of routing between different transport type

	mvn exec:java -PHttpToJMS

To run the example of Camel transport type

	mvn exec:java -PCamelTransport

To run the example of using WebServiceProvider API

	mvn exec:java -PWebServiceProvider

To run the example of showing how to create CXF JAXRS endpoint

	mvn exec:java -PJAXRS

### Documentation

This example is documented at [http://camel.apache.org/cxf-example.html](http://camel.apache.org/cxf-example.html)

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
