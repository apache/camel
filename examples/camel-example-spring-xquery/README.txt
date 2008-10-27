Spring XQuery Example
=====================

This example shows how to

 * work with files and JMS
 * transform messages using XQuery
 * use Spring XML to configure all routing rules and components

The example consumes messages from a directory, transforms them, then sends
them to a queue. It can be run using either Maven or Ant.

You will need to compile this example first:
  mvn compile

To run the example using Maven, type
  mvn camel:run

To run the example with Ant
  a. You need to have Apache ActiveMQ and Saxon installed. They can be
  downloaded from the following locations
    Apache ActiveMQ
    http://activemq.apache.org/ 
    Saxon 8.7 
    http://saxon.sourceforge.net/
    
  b. Export / Set home directories for the above as follows 
    UNIX 
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    export SAXON_HOME=<path to Saxon install directory> 
    Windows 
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory> 
    set SAXON_HOME=<path to Saxon install directory>
    
  c. To Run the example using Ant, type ant

You can see the routing rules by looking at the the Spring XML configuration
at src/main/resources/META-INF/spring

To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

For the latest & greatest documentation on how to use this example please see
  http://activemq.apache.org/camel/spring-xquery-example.html

If you hit any problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!


