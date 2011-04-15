BAM Example
===========

This example shows how to perform Business Activity Monitoring (BAM)
with Camel. It can be run using Maven or Ant.

You will need to compile this example first:
  mvn compile
  
For a background in BAM see
  http://camel.apache.org/bam.html

To run the example with Maven, type
  mvn camel:run

To run the example with Ant
  a. You need to have Hibernate Core, Entity Manager and HSQLDB installed.
  They can be downloaded from the following locations
    Hibernate Core 3.2.6 GA
      http://www.hibernate.org
    Hibernate Entity Manager 3.2.0.GA
      http://prdownloads.sourceforge.net/hibernate/hibernate-entitymanager-3.2.0.GA.zip?download
    HSQLDB 
      http://hsqldb.org/

  b. Export / Set home directories for the above as follows
    UNIX
    export HIBERNATE_CORE_HOME=<path to Hibernate install directory>
    export HIBERNATE_EM_HOME=<path to Hibernate EM install directory>
    export HSQLDB_HOME=<path to HSQLDB install directory>
    Windows
    set HIBERNATE_CORE_HOME=<path to Hibernate install directory>
    set HIBERNATE_EM_HOME=<path to Hibernate EM install directory>
    set HSQLDB_HOME=<path to HSQLDB install directory>

  c. To Run the example using Ant, type
    ant
   
You can see the BAM activies defined in
  src/main/java/org/apache/camel/example/bam/MyActivites.java
  
In the HSQL Database Explorer type
  select * from camel_activitystate
to see the states of the activities. Notice that one activity never receives
its expected message and when it's overdue Camel reports this as an error.

To stop the example hit ctrl + c

For the latest & greatest documentation on how to use this example please see
  http://camel.apache.org/bam-example.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!


