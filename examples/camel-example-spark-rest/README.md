# Camel Spark REST

### Introduction
This example shows how to use Spark REST to define REST endpoints in Camel routes using the Rest DSL
Spark requires Java 8, so you will need to use Java 8.


### Introduction
This is an example that uses the rest-dsl to define a rest services which provides three operations

- GET user/view/{id}  - View user by id
- GET user/list       - List all users
- PUT user/update     - Updates or create a user

### Build
You will need to compile this example first:

	mvn compile

### Run
To run the example type

	mvn camel:run

The rest service can be accessed from the following url

	curl http://localhost:8080/user

<http://localhost:8080/user>

For example to get a user with id 123

	curl http://localhost:8080/user/view/123

<http://localhost:8080/user/view/123>

And to list all the users

	curl http://localhost:8080/user/list

<http://localhost:8080/user/view/list>

The rest services provides Swagger API which can be accessed
from the following url in json or yaml format:

    curl http://localhost:8080/api-doc/swagger.json
    curl http://localhost:8080/api-doc/swagger.yaml

<http://localhost:8080/api-doc/swagger.json>

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>


### Forum, Help, etc
If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
