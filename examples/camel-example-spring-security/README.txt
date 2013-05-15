Camel Spring Security Example
=============

This example shows how to leverage the Spring Security to secure the camel endpoint. 

The example consumes messages from a servlet endpoint which is secured by Spring Security 
with http basic authentication, there are two service:
 "http://localhost:8080/camel/user" is for the authenticated user whose role is ROLE_USER
 "http://localhost:8080/camel/admin" is for the authenticated user whose role is ROLE_ADMIN

You will need to compile this example first:
  mvn clean install

To run the example, you need to start up the server by typing
  mvn jetty:run

To stop the server hit ctrl + c

Then you can use the script in the client directory to send the request and check the response,
or use browser to access upper urls with the user/password 
("jim/jimspassword" with the admin and user role  or "bob/bobspassword" with user role).

This example is documented at
  http://camel.apache.org/spring-security-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.

Enjoy!

------------------------
The Camel riders!
