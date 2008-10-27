Guice Example
=============

This example shows how to work with files and JMS, using Guice to boot up
Camel and configure the routes. It can be run using Maven or Ant.

The example consumes messages from a queue and writes them to the file
system.

You will need to compile this example first:
  mvn compile
  
To run the example type
  mvn exec:java

To run the example with Ant
  a. You need to have Apache ActiveMQ installed. It can be downloaded from 
    http://activemq.apache.org/

  b. Export / Set ACTIVEMQ_HOME to the top level Apache ActiveMQ intall
  directory
    UNIX
    export ACTIVEMQ_HOME=<path to ActiveMQ install directory>
    Windows
    set ACTIVEMQ_HOME=<path to ActiveMQ install directory>

  c. You need to have Guiceyfruit installed. It can be downloaded from 
    http://code.google.com/p/guiceyfruit/

  d. Export / Set GUICE_HOME to the top level Guiceyfruit intall
  directory
    UNIX
    export GUICE_HOME=<path to Guiceyfruit install directory>
    Windows
    set GUICE_HOME=<path to Guiceyfruit install directory>

  e. To Run the example using Ant, type
    ant
  or to run the example and generate visualization graphs (refer to
  http://activemq.apache.org/camel/visualisation.html), type
    ant camel.dot

You can see the routing rules by looking at the java code in the
src/main/java directory and the jndi.properties file lives in
src/main/resources/jndi.properties

To stop the example hit ctrl + c

For the latest & greatest documentation on how to use this example please see
  http://activemq.apache.org/camel/guice-jms-example.html

If you hit any problems please talk to us on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!



