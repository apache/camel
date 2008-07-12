Spring & JMS Example
====================

This example shows how to work with the Camel-JMS Component.
It can be run using Maven.

The example consumes messages from a queue and invoke the bean
with the received message.

The Server is required to be running when you try the clients.

The Server comes in two flavors:
- Normal that acts as a JMS broker
- As normal plus an AOP aspect that does audit trails of the invocation
of the business service and uses Camel for mediation of the storage of the audit message.

And for the Client we have a total of three flavors:
- Normal use the ProducerTemplate ala Spring Template style
- Using Spring Remoting for powefull "Client doesnt know at all its a remote call"
- And using the Message Endpoint pattern using the neutral Camel API

For the latest & greatest documentation on how to use this example please see:
  http://activemq.apache.org/camel/tutorial-jmsremoting.html

You will need to compile this example first:
  mvn compile

The example should run if you type:
  mvn exec:java -PCamelServer
  mvn exec:java -PCamelServerAOP

  mvn exec:java -PCamelClient
  mvn exec:java -PCamelClientRemoting
  mvn exec:java -PCamelClientEndpoint
  
You can stack the maven goals so you can compile and execute it in one command:
  mvn compile exec:java -PCamelServer

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



