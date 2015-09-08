Spring Example
==============

This example shows how to work with the simple Camel application based on the Spring Boot.

The example generates messages using timer trigger, writes them to the standard output and the mock
endpoint (for testing purposes).

This example exposes Jolokia API and Spring Boot actuators endpoints (like metrics) via the webmvc endpoint. We consider
this as the best practice - Spring Boot applications with these API exposed can be easily monitored and managed by the
3rd parties tools.

We recommend to package your application as a fat WAR. Fat WARs can be executed just as regular fat jars, but you can also
deploy them to the servlet containers like Tomcat. Fat WAR approach gives you the deployment flexibility, so we highly
recommend it.

You will need to compile this example first:
  mvn install

To run the example type
  mvn spring-boot:run

You can also execute the fat WAR directly:

  java -jar target/camel-example-spring-boot.war

You will see the message printed to the console every second.

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/spring-boot-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
