# Camel SQL Example

### Introduction
This example shows how to exchange data using a shared database table.

The example has two Camel routes. The first route insert new data into the table,
triggered by a timer to run every 5th second.

The second route pickup the newly inserted rows from the table,
process the row(s), and mark the row(s) as processed when done;
to avoid picking up the same rows again.

### Build

You will need to compile this example first:

	mvn install

### Run with maven

To run the example type

	mvn camel:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

This example uses Spring to setup and configure the database,
as well the CamelContext. You can see this in the following file:
In the src/main/resources/META-INF/spring/camel-context.xml

### Run with Karaf

To install Apache Camel in Karaf you type in the shell (we use version ${project.version}):

	feature:repo-add camel ${project.version}
	feature:install camel

First you need to install the following features in Karaf/ServiceMix with:

	feature:install camel-spring
	feature:install camel-sql

Then you need to install JDBC connection pool and the Derby Database:

	install -s mvn:commons-pool/commons-pool/1.6
	install -s mvn:commons-dbcp/commons-dbcp/1.4
	install -s mvn:org.apache.derby/derby/10.10.1.1

Then you can install the Camel example:

	install -s mvn:org.apache.camel/camel-example-sql/${project.version}

And you can see the application running by tailing the logs

	log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

### Documentation
This example is documented at <http://camel.apache.org/sql-example.html>

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>


Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
