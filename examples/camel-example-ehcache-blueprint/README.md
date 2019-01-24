# Camel Ehcache Blueprint example

### Introduction
This example shows how to use `camel-ehcache` to cache messages in a Camel route.

### Build
You will need to compile this example first:

    mvn install


### Run with Karaf
To install Apache Camel in Karaf you type in the shell (we use version ${project.version}):

    feature:repo-add camel ${project.version}
    feature:install camel

First you need to install the following features in Karaf with:

    feature:install camel-ehcache
    feature:install camel-servlet
    feature:install camel-jackson


Then you can install the example:

    install -s mvn:org.apache.camel.example/camel-example-ehcache-blueprint/${project.version}

And you can see the application running by tailing the logs:

    log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

There is a data REST service that supports the following operation(s):

- GET /data/{id} - to get data with the given id from an external service

From a web browser you can access the service using the following link(s):

    http://localhost:8181/camel-example-ehcache-blueprint/data/123    - to get the data with id 123

From the command shell you can use `curl` to access the service as shown below:

    curl -X GET -H "Accept: application/json" http://localhost:8181/camel-example-ehcache-blueprint/data/123

The first time you try to get the data it will take 3 seconds as the example (pretends to) access an external data service.
But then the data will be cached, so it will be quick afterward.


### Configuration
This example is implemented in XML DSL in the `src/main/resources/OSGI-INF/bluepring/camel-context.xml` file.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
