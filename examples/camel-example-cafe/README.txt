Cafe Example
==============

This example shows how to work with splitter and aggregator to implement a Cafe demo.
It can be run using Maven or Ant.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn exec:java

To run the example with Ant

  To Run the example using Ant, type
    ant
  or to run the example and generate visualization graphs (refer to
  http://camel.apache.org/visualisation.html), type
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
  http://camel.apache.org/cafe-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!



