# Spring Boot Example


### Introduction
This example shows how to work with the simple Camel application based on the Spring Boot.

The example generates messages using timer trigger, writes them to the standard output and the mock
endpoint (for testing purposes).

This example exposes Jolokia API and Spring Boot actuators endpoints (like metrics) via the webmvc endpoint. We consider
this as the best practice - Spring Boot applications with these API exposed can be easily monitored and managed by the
3rd parties tools.

This example packages your application as a JAR, but you can also package as a WAR and deploy to 
servlet containers like Tomcat. 

### Build
You will need to compile this example first:

	mvn install

### Run
To run the example type

	mvn spring-boot:run

You can also execute the JAR directly:

	java -jar target/camel-example-spring-boot.jar

You will see the message printed to the console every second.

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Documentation

This example is documented at <http://camel.apache.org/spring-boot-example.html>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
