JMS & File Example
==================

This example shows how to work with files and JMS. It can be run using
Maven or Ant.

The example consumes messages from a queue and writes them to the file
system.

For the latest & greatest documentation on how to use this example
please see 
  http://camel.apache.org/walk-through-an-example.html
  
You will need to compile this example first:
  mvn compile
  
The example should run if you type
  mvn exec:java -PExample
  
To run the example inside ServiceMix 4
  First install this example bundle, make sure the jms-file route service is started,
  then start the client to send the JMS message to camel route service 
  mvn exec:java -PClient

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


If you hit an problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



