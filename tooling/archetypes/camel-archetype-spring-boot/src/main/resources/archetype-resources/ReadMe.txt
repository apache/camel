Spring Boot Example
===================

This example shows how to work with the simple Camel application based on the Spring Boot.

The example generates messages using timer trigger, writes them to the standard output and the mock
endpoint (for testing purposes).

This example exposes Jolokia API and Spring Boot actuators endpoints (like metrics) via the webmvc endpoint. We consider
this as the best practice - Spring Boot applications with these API exposed can be easily monitored and managed by the
3rd parties tools.

This example packages your application as a JAR, but you can also package as a WAR and deploy to 
servlet containers like Tomcat. 

You will need to compile this example first:
  mvn install

To run the example type
  mvn spring-boot:run

You can also execute the JAR directly:

  java -jar target/${artifactId}-${version}.jar

You will see the message printed to the console every second.

To stop the example hit ctrl + c

For more help see the Apache Camel documentation

    http://camel.apache.org/

