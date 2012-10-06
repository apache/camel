Spring Java Config Example
==============

This example shows how to work with files and JMS, using Spring Java Config 
to boot up Camel and configure the routes. 

The example consumes messages from a queue and writes them to the file
system.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

You can see the routing rules by looking at the java code in the
src/main/java directory and the Spring XML configuration lives in
src/main/resources/META-INF/spring

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/spring-java-config-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
