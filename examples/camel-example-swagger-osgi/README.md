# Camel Swagger OSGi Example

### Introduction
This is an example that uses the rest-dsl to define a rest services which provides three operations

- GET user/{id}     - Find user by id
- PUT user          - Updates or create a user
- GET user/findAll  - Find all users

### Build
You will need to install this example first:

	mvn install

### Run
This example needs to be deployed on Apache Karaf/SerivceMix first:

   feature:repo-add camel ${version}
   feature:install camel
   feature:install camel-jackson
   feature:install camel-jetty
   feature:install camel-swagger-java

And then install the example

   install -s mvn:org.apache.camel.example/camel-example-swagger-osgi/${version}


The rest service can be accessed from the following url

	curl http://127.0.0.1:8080/camel-example-swagger-osgi/rest/user

<http://127.0.0.1:8080/camel-example-swagger-osgi/rest/user>

For example to get a user with id 123

	curl http://127.0.0.1:8080/camel-example-swagger-osgi/rest/user/123

<http://127.0.0.1:8080/camel-example-swagger-osgi/rest/user/123>

The rest services provides Swagger API in json or yaml format
which can be accessed from the following url

    curl http://127.0.0.1:8080/camel-example-swagger-osgi/rest/api-docs/myCamel/swagger.json
    curl http://127.0.0.1:8080/camel-example-swagger-osgi/rest/api-docs/myCamel/swagger.yaml

<http://127.0.0.1:8080/camel-example-swagger-osgi/rest/api-docs/myCamel/swagger.json>


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
