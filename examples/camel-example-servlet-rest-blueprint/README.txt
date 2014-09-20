Camel Servlet REST and OSGi Blueprint example
=============================================

This example shows how to use Servlet REST to define REST endpoints in Camel routes using the Rest DSL

This example is implemented in XML DSL in the `src/main/resources/OSGI-INF/bluepring/camel.xml` file.

Apache Karaf / ServiceMix
-------------------------
You will need to compile this example first:
  mvn compile

To install Apache Camel in Karaf you type in the shell (we use version ${project.version}):

  features:chooseurl camel ${project.version}
  features:install camel

First you need to install the following features in Karaf/ServiceMix with:

  features:install camel-servlet
  features:install camel-jackson
  features:install war

Then you can install the Camel example:

  osgi:install -s mvn:org.apache.camel/camel-example-servlet-rest-blueprint/${project.version}

And you can see the application running by tailing the logs

  log:tail

And you can use ctrl + c to stop tailing the log.


There is a user REST service that supports the following operations

 - GET /user/{id} - to view a user with the given id </li>
 - GET /user/final - to view all users</li>
 - PUT /user - to update/create an user</li>

The view operations are HTTP GET, and update is using HTTP PUT.

From a web browser you can access the first two services using the following links

      http://localhost:8181/camel-example-servlet-rest-blueprint/rest/user/123    - to view the user with id 123
      http://localhost:8181/camel-example-servlet-rest-blueprint/rest/user/findAll   - to list all users


From the command shell you can use curl to access the service as shown below:

    curl -X GET -H "Accept: application/json" http://localhost:8181/camel-example-servlet-rest-blueprint/rest/user/123
    curl -X GET -H "Accept: application/json" http://localhost:8181/camel-example-servlet-rest-blueprint/rest/user/findAll
    curl -X PUT -d "{ \"id\": 666, \"name\": \"The devil\"}" -H "Accept: application/json" http://localhost:8181/camel-example-servlet-rest-blueprint/rest/user


If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
