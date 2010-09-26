CXF WebService Proxy example
============================

An example which proxies a real webservice by a Camel application using camel-cxf component

It can be run using
Maven or Ant.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn jetty:run

The proxied webservice is located at
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident

The real webservice is located at
  http://localhost:9081/real-webservice


To run the example with Ant
  a. You need to have Apache CXF, Spring and Jetty installed. They can be
  downloaded from the following locations
    Apache CXF
    http://cxf.apache.org/
    Spring 2.5
    http://www.springframework.org/download
    Jetty6
    http://dist.codehaus.org/jetty/


  b. Export / Set home directories for the above as follows
    UNIX
    export CXF_HOME=<path to CXF install directory>
    export SPRING_HOME=<path to Spring install directory>
    export JETTY_HOME=<path to Jetty install directory>
    Windows
    set CXF_HOME=<path to CXF install directory>
    set SPRING_HOME=<path to Spring install directory>
    set JETTY_HOME=<path to Jetty install directory>

  c. To Run using Ant, type
    ant run

The webservice WSDL is exposed at:
  http://localhost:9080/camel-example-cxf-proxy/webservices/incident?wsdl


To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources

For the latest & greatest documentation on how to use this example please see
  http://camel.apache.org/cxf-proxy-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



