# Camel Spring Web Services Example

### Introduction
This example shows how to expose a SOAP-based web service using Camel and Spring Web Services.

### Build
You will need to compile this example first:
  
    mvn clean install

### Run
To run the example, you need to start up the server by typing

	  mvn jetty:run

To stop the server hit <kbd>ctrl</kbd>+<kbd>c</kbd>


The web service endpoint address is:
  <http://localhost:8080/increment>

The WSDL is available at:
  <http://localhost:8080/increment/increment.wsdl>


You can test the web service using for example SOAP-UI. This excellent tool is freely available from http://www.soapui.org.
There's a ready to use SOAP-UI project available in the `client` directory.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
