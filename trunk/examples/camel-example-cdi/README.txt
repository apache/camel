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
is created and started.

Additionally there are maven profiles being defined by this example (see pom.xml for details)
which allow to test using arquillian the project in weld container, jboss-as, ...

To run the example with Arquillian & JBossAS 7.x managed, type
  mvn test -Parquillian-jbossas-managed
  
And for Arquillian & Weld
  mvn test -Parquillian-weld-ee-embedded

Please note that both the JBoss as well as Weld maven artifacts are pretty
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
