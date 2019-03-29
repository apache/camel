# CXF WebService Proxy example

### Introduction

An example which proxies a real web service by a Camel application using the camel-cxf component

### Build
You will need to compile this example first:

	mvn compile

### Run

To run the example type:
	
	mvn camel:run -Dmaven.test.skip=true

The proxied webservice is located at

	http://localhost:${proxy.port}/camel-example-cxf-proxy/webservices/incident

<http://localhost:9080/camel-example-cxf-proxy/webservices/incident>

The real webservice is located at

	http://localhost:${real.port}/real-webservice
	
<http://localhost:9081/real-webservice>

The webservice WSDL is exposed at:

	http://localhost:${proxy.port}/camel-example-cxf-proxy/webservices/incident?wsdl
	
<http://localhost:9080/camel-example-cxf-proxy/webservices/incident?wsdl>

Because we use dynamic port numbers, you have to check the console to get the used one.
To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

To make a SOAP call open soapUI or another SOAP query tool and create a new
project w/WSDL of <http://localhost:${proxy.port}/camel-example-cxf-proxy/webservices/incident?wsdl>.
Then make SOAP requests of this format:

	<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
	                  xmlns:rep="http://reportincident.example.camel.apache.org">
	   <soapenv:Header/>
	   <soapenv:Body>
	      <rep:inputReportIncident>
	         <incidentId></incidentId>
	         <incidentDate>2011-11-18</incidentDate>
	         <givenName>Bob</givenName>
	         <familyName>Smith</familyName>
	         <summary>Bla bla</summary>
	         <details>More bla</details>
	         <email>your@email.org</email>
	         <phone>12345678</phone>
	      </rep:inputReportIncident>
	   </soapenv:Body>
	</soapenv:Envelope>

### Configuration

You can change `${proxy.port}` and `${real.port}` via configuration file `src/main/resources/incident.properties`


### Forum, Help, etc 

If you hit an problems please let us know on the Camel Forums
  <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
