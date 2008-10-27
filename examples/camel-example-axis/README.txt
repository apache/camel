Axis Example
============

This example shows how Camel integrates with older frameworks such as Apache Axis 1.4.
It can be run using Maven, and you will need to compile this example first:
  mvn compile

The example exposes a webservice using Apache Axis that uses Camel to send the input
as a message to an endpoint. The endpoint is a file endpoint that stores a backup of the request.
The purpose is more to demonstrate the integration between Axis, Spring and Camel than
other concepts in Camel such as its powerful routing and mediation framework.

To run the example type
  mvn jetty:run

You stop Jetty using ctrl + c

Then you can hit the url: http://localhost:8080/camel-example-axis/services in a browser
to see the exposed webservice by Apache Axis.

The wsdl can be reached by clicking on the wsdl link. You can use a webservie tools to try
it out online such as SoapUI.

This project uses log4j as the logging and log4j.properties is located in src/main/resources 

For the latest & greatest documentation on how to use this example please see
  http://activemq.apache.org/camel/tutorial-axis-camel.html

If you hit any problems please talk to us on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!



