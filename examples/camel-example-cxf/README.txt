CXF Example
===========

This example shows how to work with CXF and Camel. It can be run using
Maven or Ant.

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
 Then install this bundle into ServiceMix, and use those java client to call the services
 mvn exec:java -PHttpToJMS.Client
 mvn exec:java -PCamelTransport.Client
 mvn exec:java -PWebServiceProvider.Client
 mvn exec:java -PJAXRS.Client
 

To run the example with Ant
  a. You need to have Apache ActiveMQ , Apache CXF, Spring and Jetty installed. They can be
  downloaded from the following locations
    Apache ActiveMQ
    http://activemq.apache.org/
    Apache CXF
    http://cxf.apache.org/
    Spring 2.5
    http://www.springframework.org/download
    Jetty6
    http://dist.codehaus.org/jetty/


  b. Export / Set home directories for the above as follows
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    export CXF_HOME=<path to CXF install directory>
    export SPRING_HOME=<path to Spring install directory>
    export JETTY_HOME=<path to Jetty install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    set CXF_HOME=<path to CXF install directory>
    set SPRING_HOME=<path to Spring install directory>
    set JETTY_HOME=<path to Jetty install directory>

  c. To Run example of routing between different transport type using Ant, type
    ant

     To run the example of Camel transport type
    ant runCamelTransport

     To run the example of using WebServiceProvider API
    ant runWebServiceProvider
    
    To run the example of showing how to create CXF JAXRS endpoint
    ant runJAXRS


To stop the example hit ctrl + c

For the latest & greatest documentation on how to use this example please see
  http://camel.apache.org/cxf-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



