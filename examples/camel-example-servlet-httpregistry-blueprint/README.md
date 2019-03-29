# Camel Servlet HttpRegistry Blueprint example

### Introduction

This example shows how to use `camel-servlet` [HttpRegistry](https://github.com/apache/camel/blob/master/components/camel-servlet/src/main/java/org/apache/camel/component/servlet/HttpRegistry.java) so that a `CamelServlet` can serve multiple OSGi bundles.

### Build

You will need to compile this example first:

    mvn install

### Run

To install Apache Camel in Karaf you type in the shell (we use version ${project.version}):

    feature:repo-add camel ${project.version}
    feature:install camel

First you need to install the following features in Karaf/ServiceMix with:

    feature:install camel-servlet
    feature:install war

Then you can install the example:

    install -s mvn:org.apache.camel.example/camel-example-servlet-httpregistry-blueprint/${project.version}

And you can see the application running by tailing the logs

    log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

There is a servlet that supports the following operation:

- POST /camel/services/hello - to echo the request body

From the command shell you can use `curl` to post a request to the servlet as shown below:

    curl -X POST -H "Content-Type: text/plain" -d "Hello World" http://localhost:8181/camel/services/hello


### Configuration

This example is implemented in XML DSL in the [src/main/resources/OSGI-INF/blueprint/camel-context.xml](src/main/resources/OSGI-INF/blueprint/camel-context.xml) file.


### Forum, Help, etc

If you hit problems please let us know on the Camel Forums:
    <http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
