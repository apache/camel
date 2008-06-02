Spring Example
==============

This example shows how to work with files and JMS, using Spring to boot up
Camel and configure the routes. It can be run using Maven or Ant.

The example consumes messages from a queue and writes them to the file
system.

To run the example type
  mvn camel:run

To run the example with Ant
  a. You need to have Apache ActiveMQ installed. It can be downloaded from 
    http://activemq.apache.org/

  b. Export / Set ACTIVEMQ_HOME to the top level Apache ActiveMQ intall
  directory
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>

  c. To Run the example using Ant, type
    ant
  or to run the example and generate visualization graphs (refer to
  http://activemq.apache.org/camel/visualisation.html), type
    ant camel.dot

You can see the routing rules by looking at the java code in the
src/main/java directory and the Spring XML configuration lives in
src/main/resources/META-INF/spring

To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

For the latest & greatest documentation on how to use this example please see
  http://activemq.apache.org/camel/spring-example.html

If you hit any problems please talk to us on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!



