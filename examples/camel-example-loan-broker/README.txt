Loan Broker Example
====================

This example shows how to use Camel to implement the EIP's loan broker example.
It can be run using Maven.

The example has two version, one is queue version which leverages the message
queue to combinate the credit agency and bank loan quote processing and it
uses the InOnly exchage pattern; the other is web service version which shows
how to integrate the credit agency and bank web services together and it uses
the InOut exchange pattern.

For the latest & greatest documentation on how to use this example please see:
  http://cwiki.apache.org/CAMEL/example-loan-broker.html

You will need to compile this example first:
  mvn compile

The example of queue version should run if you type
  mvn exec:java -PQueue.LoanBroker
  mvn exec:java -PQueue.Client

The exmple of WebServices version
  mvn exec:java -PWS.LoanBroker
  mvn exec:java -PWS.Client

To run the example with Ant
  a. You need to have Apache ActiveMQ , Apache CXF and Spring installed. They can be
  downloaded from the following locations
    Apache ActiveMQ
    http://activemq.apache.org/
    Apache CXF
    http://cxf.apache.org/
    Spring 2.5
    http://www.springframework.org/download


  b. Export / Set home directories for the above as follows
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    export CXF_HOME=<path to CXF install directory>
    export SPRING_HOME=<path to Spring install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    set CXF_HOME=<path to CXF install directory>
    set SPRING_HOME=<path to Spring install directory>

  c. To Run the server of the example of WebServices version using Ant, type
    ant

     To Run the client of the example of WebServices version using Ant, type
    ant runWebClient

     To run the server of the example of Queue version using Ant , type
    ant runQueueServer

     To run the client of the example of Queue version using Ant , type
    ant runQueueClient

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



