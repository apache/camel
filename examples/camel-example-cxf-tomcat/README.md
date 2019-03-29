# Camel CXF (code first) and Apache Tomcat example

### Introduction
An example which uses code-first to expose a web service in Camel running on Apache Tomcat.

### Build
It can be run using Maven.

You will need to first need to build the example:

	mvn clean install

### Run

To run the example deploy it in Apache Tomcat by copying the .war located
in the target directory to the deploy folder of Apache Tomcat.  Alternatively,
if your Tomcat installation is set up to use the Tomcat Maven plugin
([http://mojo.codehaus.org/tomcat-maven-plugin/usage.html](http://mojo.codehaus.org/tomcat-maven-plugin/usage.html)), you can simply
run `mvn tomcat:deploy` (also `tomcat:undeploy`, `tomcat:redeploy`, etc.)
to install the WAR file.  

The webservice is located at

	http://localhost:8080/camel-example-cxf-tomcat/webservices/incident?wsdl

[http://localhost:8080/camel-example-cxf-tomcat/webservices/incident?wsdl](http://localhost:8080/camel-example-cxf-tomcat/webservices/incident?wsdl)

You can run a sample client using the "mvn exec:java" command, or, within
soapUI, making sample SOAP requests such as the following:

	<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
	    <soap:Body>
	        <ns1:reportIncident xmlns:ns1="http://incident.cxf.example.camel.apache.org/">
	            <arg0>
	                <details>blah blah</details>
	                <email>davsclaus@apache.org</email>
	                <familyName>Smith</familyName>
	                <givenName>Bob</givenName>
	                <incidentDate>2011-11-25</incidentDate>
	                <incidentId>123</incidentId>
	                <phone>123-456-7890</phone>
	                <summary>blah blah summary</summary>
	            </arg0>
	        </ns1:reportIncident>
	    </soap:Body>
	</soap:Envelope>

	<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
	    <soap:Body>
	        <ns1:statusIncident xmlns:ns1="http://incident.cxf.example.camel.apache.org/">
	            <arg0>
	                <incidentId>456</incidentId>
	            </arg0>
	        </ns1:statusIncident>
	    </soap:Body>
	</soap:Envelope>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
