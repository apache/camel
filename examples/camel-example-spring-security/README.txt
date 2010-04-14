Camel Spring Security Example
=============

This example shows how to leverage the Spring Security to secure the camel endpoint. 
It can be run using Maven.

The example consumes messages from a servlet endpoint which is secured by Spring Security 
with http basic authentication, there are two service:
 "http://localhost:8080/camel/user" is for the authenticated user whose role is ROLE_USER
 "http://localhost:8080/camel/admim" is for the authenticated user whose role is ROLE_ADMIN

You will need to compile this example first:
  mvn clean install

To run the example, you need to start up the server by typing
  mvn jetty:run

To stop the server hit ctrl + c

Then you can use the script in the client directory to send the request and check the response.

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

For the latest & greatest documentation on how to use this example please see
  http://camel.apache.org/spring-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.

Enjoy!

------------------------
The Camel riders!
