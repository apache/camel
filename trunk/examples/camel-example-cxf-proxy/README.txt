CXF WebService Proxy example
============================

An example which proxies a real web service by a Camel application using the camel-cxf component

You will need to compile this example first:
  mvn compile

To run the example type:
  mvn camel:run

The proxied webservice is located at
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident

The real webservice is located at
  http://localhost:9081/real-webservice

The webservice WSDL is exposed at:
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident?wsdl

To stop the example hit ctrl + c

To make a SOAP call open soapUI or another SOAP query tool and create a new
project w/WSDL of http://localhost:9080/camel-example-cxf-proxy/webservices/incident?wsdl.
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
         <email>davsclaus@apache.org</email>
         <phone>12345678</phone>
      </rep:inputReportIncident>
   </soapenv:Body>
</soapenv:Envelope>

This example is documented at
  http://camel.apache.org/cxf-proxy-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel Riders!

