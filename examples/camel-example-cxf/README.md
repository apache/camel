# Camel CXF Example

### Introduction

This example shows how to work with CXF and Camel.

The Camel CXF example is a demo of the camel-cxf component to show how to route messages between CXF endpoints,
with one endpoint consuming a SOAP over HTTP request while the other providing a SOAP over JMS request for the actual CXF Service endpoint.
The Camel router just routes the SOAP over HTTP CXF client request to the SOAP over JMS CXF service.

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


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
