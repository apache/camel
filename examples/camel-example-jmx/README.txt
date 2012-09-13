JMX Example
===========

This example shows how to work with the Camel-JMX component.

The example creates a simple MBean, registers a route to listen for
notification events on that bean and creates another route that calls
the MBean.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

You can see the routing rules by looking at the java code in the
src/main/java directory and the Spring XML configuration lives in
src/main/resources/META-INF/spring

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/jmx-component-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
