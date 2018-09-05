# Camel SQL Blueprint Example

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

This example uses Blueprint to setup and configure the database,
as well the CamelContext. You can see this in the following file:
In the src/main/resources/OSGI-INF/blueprint/camel-context.xml

### Run with Karaf
You will need to install this example first to your local maven repository with:

	mvn install

Then you can install this example from the shell using this example's `features.xml`
for easy provisioning:

	feature:repo-add mvn:org.apache.camel.example/camel-example-sql-blueprint/${version}/xml/features
	feature:install camel-example-sql-blueprint

And you can see the application running by tailing the logs:

	log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
