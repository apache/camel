Camel Spring Web Services Example
=============

This example shows how to expose a SOAP-based web service using Camel and Spring Web Services.

The web service endpoint address is:
  http://localhost:8080/increment
 
The WSDL is available at:
  http://localhost:8080/increment/increment.wsdl

You will need to compile this example first:
  mvn clean install

To run the example, you need to start up the server by typing
  mvn jetty:run

To stop the server hit ctrl + c

You can test the web service using for example SOAP-UI. This excellent tool is freely available from http://www.soapui.org. 
There's a ready to use SOAP-UI project available in the "client" directory. 

This example is documented at
  http://camel.apache.org/spring-ws-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.

Enjoy!

------------------------
The Camel riders!
