# Camel MyBatis Example

### Introduction

This example shows how to exchange data using a shared database table.

The example has two Camel routes. The first route insert new data into the table,
triggered by a timer to run every 5th second.

The second route pickup the newly inserted rows from the table,
process the row(s), and mark the row(s) as processed when done;
to avoid picking up the same rows again.

### Build

You will need to install this example first to your local maven repository with:

	mvn install

### Run

This example requires running in Apache Karaf / ServiceMix

You can install this example from the shell using this example's `features.xml`
for easy provisioning.

	feature:repo-add camel ${version}
	feature:install camel
	feature:repo-add mvn:org.apache.camel.example/camel-example-mybatis/${version}/xml/features
	feature:install camel-example-mybatis

And you can see the application running by tailing the logs

	log:tail

And you can use <kbd>ctrl</kbd>+<kbd>c</kbd> to stop tailing the log.

### Configuration

This example uses OSGi Blueprint to setup and configure the database,
as well the CamelContext. You can see this in the following file:
In the `src/main/resources/OSGI-INF/blueprint/camel-mybatis.xml`

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!
