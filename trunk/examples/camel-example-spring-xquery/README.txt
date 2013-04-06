Spring XQuery Example
=====================

This example shows how to

 * work with files and JMS
 * transform messages using XQuery
 * use Spring XML to configure all routing rules and components

The example consumes messages from a directory, transforms them, then sends
them to a queue. 

You will need to compile this example first:
  mvn compile

To run the example using Maven, type
  mvn camel:run

You can see the routing rules by looking at the the Spring XML configuration
at src/main/resources/META-INF/spring

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/spring-xquery-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
