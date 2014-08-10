Camel Servlet REST and Apache Tomcat example
=======================================

This example shows how to use Servlet REST to define REST endpoints in Camel routes using the Rest DSL

This example is implemented in both the Java and XML DSLs. By default the Java DSL is in use.
You can change this in the src/main/webapps/WEB-INF/web.xml file

For Java DSL the routes are defined in Java code, in the org.apache.camel.example.rest.UserRouteBuilder class.
And for XML DSL the routes are define in XML code, in the src/main/resources/camel-config-xml.xml file.

You will need to package this example first:
  mvn package

To run the example deploy it in Apache Tomcat by copying the .war to the
deploy folder of Apache Tomcat.

And then hit this url from a web browser which has further instructions
  http://localhost:8080/camel-example-servlet-rest-tomcat

Included in this example is an api browser using Swagger. You can see the API from this url:
  http://localhost:8080/camel-example-servlet-rest-tomcat/api-docs

You can also try the example from Maven using
   mvn jetty:run

... and use the following url

  http://localhost:8080/

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
