Guice Example
=============

This example shows how to work with files and JMS, using Guice to boot up
Camel and configure the routes.

The example consumes messages from a queue and writes them to the file
system.

You will need to compile this example first:

  mvn compile
  
To run the example type:

  mvn exec:java

Alternatively to run the example you can also make use of the Guice Maven
Plugin provided by Camel, so you can instead type:

  mvn guice:run

See the POM of this example about how to make use of this Maven Plugin

You can see the routing rules by looking at the java code in the
src/main/java directory and the guicejndi.properties file lives in
src/main/resources/guicejndi.properties

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/guice-jms-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
