Camel SQL Example
=================

This example shows how to exchange data using a shared database table.

The example has two Camel routes. The first route insert new data into the table,
triggered by a timer to run every 5th second.

The second route pickup the newly inserted rows from the table,
process the row(s), and mark the row(s) as processed when done;
to avoid picking up the same rows again.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

To stop the example hit ctrl + c

This example uses Spring to setup and configure the database,
as well the CamelContext. You can see this in the following file:
In the src/main/resources/META-INF/spring/camel-context.xml

This example is documented at
  http://camel.apache.org/sql-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
