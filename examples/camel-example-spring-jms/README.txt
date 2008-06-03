Spring & JMS Example
====================

This example shows how to work with the Camel-JMS Component.
It can be run using Maven.

The example consumes messages from a queue and invoke the bean
with the received message

For the latest & greatest documentation on how to use this example please see:
  http://cwiki.apache.org/CAMEL/tutorial-jmsremoting.html

The example should run if you type
  mvn exec:java -PCamelServer
  mvn exec:java -PCamelClient
  mvn exec:java -PCamelClientRemoting

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


