BAM Example
===========

This example shows how to perform Business Activity Monitoring (BAM)
with Camel. 

You will need to compile this example first:
  mvn compile
  
For a background in BAM see
  http://camel.apache.org/bam.html

To run the example with Maven, type
  mvn camel:run

You can see the BAM activies defined in
  src/main/java/org/apache/camel/example/bam/MyActivites.java
  
In the HSQL Database Explorer type
  select * from camel_activitystate
to see the states of the activities. Notice that one activity never receives
its expected message and when it's overdue Camel reports this as an error.

To stop the example hit ctrl + c

This example is documented at
  http://camel.apache.org/bam-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!
