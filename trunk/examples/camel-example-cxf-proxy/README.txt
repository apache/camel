CXF WebService Proxy example
============================

An example which proxies a real webservice by a Camel application using camel-cxf component

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

The proxied webservice is located at
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident

The real webservice is located at
  http://localhost:9081/real-webservice

The webservice WSDL is exposed at:
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident?wsdl

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/cxf-proxy-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



