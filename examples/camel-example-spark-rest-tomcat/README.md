# Camel Spark REST and Apache Tomcat example

### Introduction
This example shows how to use Spark REST to define REST endpoints in Camel routes using the Rest DSL
Spark requires Java 8, so you will need to use Java 8.


### Build
You will need to package this example first:

	mvn package

### Run

To run the example deploy it in Apache Tomcat by copying the `.war` to the
deploy folder of Apache Tomcat.

And then hit this url from a web browser which has further
instructions

	http://localhost:8080/camel-example-spark-rest-tomcat
<http://localhost:8080/camel-example-spark-rest-tomcat>

You can also try the example from Maven using

	mvn jetty:run

... and use the following url

	http://localhost:8080/
<http://localhost:8080/>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
