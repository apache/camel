CXF Example
===========

This example shows how to work with CXF and Camel. 

You will need to compile this example first:
  mvn compile

To run the example of routing between different transport type
  mvn exec:java -PHttpToJMS

To run the example of Camel transport type
  mvn exec:java -PCamelTransport

To run the example of using WebServiceProvider API
  mvn exec:java -PWebServiceProvider
  
To run the example of showing how to create CXF JAXRS endpoint
  mvn exec:java -PJAXRS

To run the example within ServiceMix 4
 First, you need to install the camel-cxf, camel-jetty features into ServiceMix4
 Then install this bundle into ServiceMix, and use the following Java clients 
 to call the services:
 
 mvn exec:java -PHttpToJMS.Client
 mvn exec:java -PCamelTransport.Client
 mvn exec:java -PWebServiceProvider.Client
 mvn exec:java -PJAXRS.Client
 
To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/cxf-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel Riders!



