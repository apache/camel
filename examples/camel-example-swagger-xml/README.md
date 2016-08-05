# Camel Swagger example

### Introduction
This is an example that uses the rest-dsl to define a rest services which provides three operations

- GET user/{id}     - Find user by id
- PUT user          - Updates or create a user
- GET user/findAll  - Find all users

The example also embeds the swagger ui.

### Build
You will need to compile this example first:

	mvn compile

### Compile
To run the example type

	mvn jetty:run

The example is built as a WAR which can also be deployed in a WAR container such as Apache Tomcat.

The example has documentation in the home.html page which you can access using the following url

	http://localhost:8080/camel-example-swagger-xml/

<http://localhost:8080/camel-example-swagger-xml/>

This example implements the rest-dsl in XML in the camel-config.xml file. For an example that
is using Java code, see the `camel-example-swagger-cdi`.

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
