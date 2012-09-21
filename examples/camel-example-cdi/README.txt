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
  
Remarks : 
- When we use the camel plugin, a local CDI container is created and started
- Two maven profiles have been defined in the example to run the code into a Glassfish 

mvn deploy -Pglassfish

and JBoss AS 

mvn deploy --Pjboss 

This example is documented at
  http://camel.apache.org/cdi-example.html

If you hit any problems please talk to us on the Camel Forums
  http://camel.apache.org/discussion-forums.html
  

Please help us make Apache Camel better - we appreciate any feedback you
may have.  Enjoy!

------------------------
The Camel riders!
