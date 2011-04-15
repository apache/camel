POJO Messaging Example
======================

This example shows that you don't need to learn Camel's super cool DSLs 
if you don't want to. Camel has a set of annotations that allow you to 
produce, consume or route messages to endpoints. You can run it using
Maven or Ant.

For detailed documentation on how to use this example please see 
  http://camel.apache.org/pojo-messaging-example.html
  
The example should run if you type
  mvn compile camel:run

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



