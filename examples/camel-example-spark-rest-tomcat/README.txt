Camel Spark REST and Apache Tomcat example
=======================================

This example shows how to use Spark REST to define REST endpoints in Camel routes using the Rest DSL

You will need to package this example first:
  mvn package

Spark requires Java 8, so you will need to use Java 8.

To run the example deploy it in Apache Tomcat by copying the .war to the
deploy folder of Apache Tomcat.

And then hit this url from a web browser which has further
instructions
  http://localhost:8080/camel-example-spark-rest-tomcat

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
