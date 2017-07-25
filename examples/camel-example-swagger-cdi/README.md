# Camel Swagger cdi Example

### Introduction
This is an example that uses the rest-dsl to define a rest services which provides three operations

- GET user/{id}     - Find user by id
- PUT user          - Updates or create a user
- GET user/findAll  - Find all users

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

	curl http://localhost:8080/user/123

<http://localhost:8080/user/123>

The rest services provides Swagger API in json or yaml format
which can be accessed from the following url

    curl -H "Accept: application/json" http://localhost:8080/api-doc
    curl -H "Accept: application/yaml" http://localhost:8080/api-doc


<http://localhost:8080/api-doc>

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
