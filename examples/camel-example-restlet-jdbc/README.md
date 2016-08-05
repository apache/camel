# Camel Restlet and JDBC Example

### Introduction
An example which shows how to expose CRUD operations with REST DSL interface and JDBC implementation

Two implementations are available

* XML Rest DSL (default)
* Java Rest DSL


### Build

You will need to compile this example first:

	mvn install

### Run

### Run Java-REST-DSL
To run with Java-REST-DSL use:

	mvn jetty:run -Dimpl=java-rest-dsl

### Run XML-REST-DSL
To run with XML-REST-DSL use:

	mvn jetty:run -Dimpl=xml-rest-dsl

### Check
To create an person, make a http POST request with firstName and lastName parameters:

	curl -X POST -d "firstName=test&lastName=person" http://localhost:8080/rs/persons

*Result :*

	[{ID=1, FIRSTNAME=test, LASTNAME=person}]

To update an existing person, make a http PUT request with firstName and lastName parameters:

	curl -X PUT -d "firstName=updated&lastName=person" http://localhost:8080/rs/persons/1

To retrieve an existing person, make a http GET request with the personId as part of the url:

	curl http://localhost:8080/rs/persons/1

*Result :*		

	[{ID=1, FIRSTNAME=updated, LASTNAME=person}]


To retrieve all the existing persons, make a http GET request to persons url:

	curl http://localhost:8080/rs/persons

*Result :*

	[{ID=1, FIRSTNAME=updated, LASTNAME=person}]

To delete an existing person, make a http DELETE request with the personId as part of the url:

	curl -X DELETE  http://localhost:8080/rs/persons/1

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
