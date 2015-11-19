camel-example-swagger-cdi
=========================

This is an example that uses the rest-dsl to define a rest services which provides three operations

- GET user/{id}     - Find user by id
- PUT user          - Updates or create a user
- GET user/findAll  - Find all users

The rest service can be accessed from the following url

    http://localhost:8080/user

For example to get a user with id 123

   http://localhost:8080/user/123

The rest services provides Swagger API which can be accessed from the following url

    http://localhost:8080/api-docs


You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

To stop the example hit ctrl + c

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
