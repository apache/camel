# Camel JDBC Example

### Introduction

This example shows how to use camel-jdbc component with an embedded
Apache Derby database.

### Build

First compile this example:

	mvn compile

### Compile

Now to run the example type

	mvn camel:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>

### Configuration

This example uses Spring to setup and configure the database,
as well the CamelContext.

You can see this in the following file:
* `src/main/resources/META-INF/spring/camel-context.xml`

The spring config setups three routes as follow:

* `sample-generator-route`
 - This route will generate sample data into database upon Camel starts.
* `query-update-route-part1/query-update-route-part2`
 - These two are connected together. It first query the database for NEW record to be process, invoke RecordProcess bean to do the work, then update the record as DONE so not to re-process on next polled.


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
