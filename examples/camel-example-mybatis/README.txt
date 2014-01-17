Camel MyBatis Example
=====================

This example shows how to exchange data using a shared database table.

The example has two Camel routes. The first route insert new data into the table,
triggered by a timer to run every 5th second.

The second route pickup the newly inserted rows from the table,
process the row(s), and mark the row(s) as processed when done;
to avoid picking up the same rows again.

You will need to install this example first to your local maven repository with:
  mvn install

This example requires running in Apache Karaf / ServiceMix

You can install this example from the shell using this example's "features.xml"
for easy provisioning.

  features:addUrl mvn:org.apache.camel/camel-example-mybatis/${version}/xml/features
  features:install camel-example-mybatis

And you can see the application running by tailing the logs

  log:tail

And you can use ctrl + c to stop tailing the log.

This example uses OSGi Blueprint to setup and configure the database,
as well the CamelContext. You can see this in the following file:
In the src/main/resources/OSGI-INF/blueprint/camel-mybatis.xml

This example is documented at
  http://camel.apache.org/mybatis-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
