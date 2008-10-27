JMS & File Example
==================

This example shows how to work with files and JMS. It can be run using
Maven or Ant.

The example consumes messages from a queue and writes them to the file
system.

For the latest & greatest documentation on how to use this example
please see 
  http://activemq.apache.org/camel/walk-through-an-example.html
  
You will need to compile this example first:
  mvn compile
  
The example should run if you type
  mvn exec:java

To run the example with Ant
  a. You need to have Apache ActiveMQ installed. It can be downloaded from 
    http://activemq.apache.org/

  b. Export / Set ACTIVEMQ_HOME to the top level Apache ActiveMQ install
  directory
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>

  c. To run the example using Ant, type
    ant

To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

If you hit an problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



