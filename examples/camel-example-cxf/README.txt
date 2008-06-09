CXF Example
===========

This example shows how to work with CXF and Camel. It can be run using
Maven or Ant.

To run the example type
  mvn compile exec:java

To run the example with Ant
  a. You need to have Apache ActiveMQ and Apache CXF installed. They can be
  downloaded from the following locations
    Apache ActiveMQ
    http://activemq.apache.org/ 
    Apache CXF 
    http://incubator.apache.org/cxf/

  b. Export / Set home directories for the above as follows
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    export CXF_HOME=<path to CXF install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    set CXF_HOME=<path to CXF install directory>

  c. To Run the example using Ant, type
    ant

To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

For the latest & greatest documentation on how to use this example please see
  http://activemq.apache.org/camel/cxf-example.html

If you hit any problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



