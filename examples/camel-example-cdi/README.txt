CDI Example
===========

This example shows how to work with Camel using CDI to configure components,
endpoints and beans.

The example consumes messages from a queue and writes them to the file
system.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run
  
You can see the routing rules by looking at the java code in the
  src/main/java directory

  To stop the example hit ctrl + c
  
When we launch the example using the camel maven plugin, a local CDI container
is created and started. Additionally there're two maven profiles being defined
by this example (see pom.xml for details) so that using maven we can easily embed
and deploy the example into an application server.

To run the example using JBoss type
  mvn deploy -Pjboss
  
And for Glassfish
  mvn deploy -Pglassfish

Please note that both the JBoss as well as Glassfish maven artifacts are pretty
big in size so be patient as maven downloads them into your local repository for
the first time.

This example is documented at
  http://camel.apache.org/cdi-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html
  

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
