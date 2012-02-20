SSH Example
=====================

This example shows how use SSH with Camel. It can be run using Maven.

This example is built assuming you have a running Apache ServiceMix container with the default SSH port 8101 and
username / password of smx/smx.

Running from cmd line outside OSGi container
============================================

You will need to compile this example first:
  mvn compile

To run the example using Maven type
  mvn camel:run

To stop the example hit ctrl + c



Running inside OSGi container
=============================

You will need to compile and install this example first:
  mvn compile install

If using Apache Karaf / Apache ServiceMix you can install this example
from the shell

  > features:addurl mvn:org.apache.camel/camel-example-ssh/<camel version>/xml/features
  > features:install camel-example-ssh

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!

