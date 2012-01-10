OSGi Example
=====================

This example shows how use OSGi with Camel. It can be run using Maven or
Ant.

Running from cmd line outside OSGi container
============================================

You will need to compile this example first:
  mvn compile

To run the example using Maven type
  mvn camel:run

To run the example using Ant type
  ant

To stop the example hit ctrl + c



Running inside OSGi container
=============================

You will need to compile and install this example first:
  mvn compile install

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell

  osgi:install mvn:org.apache.camel/camel-example-osgi/2.5.0

      (substitute 2.5.0 with the Camel version number)

Then start the bundle by starting the id it was assigned during installation

  osgi:start 182

      (substitute 182 with the id of the bundle)



To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

You can also run this example in a osgi container like ServiceMix kernel
 http://cwiki.apache.org/CAMEL/how-to-run-camel-in-a-osgi-container.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!

