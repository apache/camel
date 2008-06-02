Extract Transform Load (ETL) Example
====================================

This example shows how to use Camel as an ETL tool. It can be run using
Maven or Ant
  http://activemq.apache.org/camel/etl.html

For a full description of this example please see
  http://activemq.apache.org/camel/etl-example.html

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

To use log4j as the logging framework add this to the pom.xml:
    <dependency>
      <groupId>log4j</groupId>
      <artifactId>log4j</artifactId>
    </dependency>
and log4j.properties is located in src/main/resources 

If you hit any problems please let us know on the Camel Forums
  http://activemq.apache.org/camel/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!


