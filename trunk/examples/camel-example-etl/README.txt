Extract Transform Load (ETL) Example
====================================

This example shows how to use Camel as an ETL tool. It can be run using
Maven or Ant
  http://camel.apache.org/etl.html

For a full description of this example please see
  http://camel.apache.org/etl-example.html

You will need to compile this example first:
  mvn compile

To run the example type
  mvn camel:run

To run the example with Ant
  a. You need to have Hibernate Core, Entity Manager, HSQLDB and Juel
  installed. They can be downloaded from the following locations
    Hibernate Core 3.2.6 GA
      http://www.hibernate.org
    Hibernate Entity Manager 3.2.0.GA
      http://prdownloads.sourceforge.net/hibernate/hibernate-entitymanager-3.2.0.GA.zip?download
    HSQLDB
      http://hsqldb.org/
    Juel
      http://juel.sourceforge.net/

  b. Export / Set home directories for the above as follows
    UNIX
    export HIBERNATE_CORE_HOME=<path to Hibernate install directory>
    export HIBERNATE_EM_HOME=<path to Hibernate EM install directory>
    export HSQLDB_HOME=<path to HSQLDB install directory>
    export JUEL_HOME=<path to Juel install directory>
    Windows
    set HIBERNATE_CORE_HOME=<path to Hibernate install directory>
    set HIBERNATE_EM_HOME=<path to Hibernate EM install directory>
    set HSQLDB_HOME=<path to HSQLDB install directory>
    set JUEL_HOME=<path to Juel install directory>
      
  c. To Run the example using Ant, type
    ant

You can see the routing rules by looking at the java code in the src/main/java
directory and the Spring XML configuration lives in
  src/main/resources/META-INF/spring

To stop the example hit ctrl + c


If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!


