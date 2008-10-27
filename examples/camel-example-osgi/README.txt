OSGi Example
=====================

This example shows how use OSGi with Camel. It can be run using Maven or
Ant.

You will need to compile this example first:
  mvn compile

To run the example using Maven type
  mvn camel:run

To run the example using Ant type
  ant

To stop the example hit ctrl + c

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

You can also run this example in a osgi container like ServiceMix kernel
 http://cwiki.apache.org/CAMEL/how-to-run-camel-in-a-osgi-container.html

If you hit any problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!

