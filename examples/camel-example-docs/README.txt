Documentation Example
=====================

This example creates a bunch of routes to show how the Maven reporting
tools can visualise routes
  http://activemq.apache.org/camel/docs-example.html

This example also acts as an integration test case for the GraphViz
visualisation feature 
  http://activemq.apache.org/camel/visualisation.html
  
You will need to compile this example first:
  mvn compile

NOTE before you run this example you MUST install GraphViz so that
the 'dot' executable is available on your path. 
See the camel:dot documentation for more information
  http://activemq.apache.org/camel/camel-dot-maven-goal.html

To run the example using Maven and generate the documentation type
  mvn camel:dot

The reports should be generated in
  target/site/cameldoc/index.html

To run the example using Ant and generate the documentation type
  ant

If the dot executable is in your path, .svg images will be generated for
any .dot files under
  target/site/cameldoc

You can see the routing rules by looking at the java code in the
src/main/java directory and the Spring XML configuration file in
src/main/resources/META-INF/spring

If you hit any problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!



