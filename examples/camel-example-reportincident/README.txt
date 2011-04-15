Report Incident Example
=======================

An example based on real life use case for reporting incidents using webservice
that are transformed and send as emails to a backing system

It can be run using
Maven or Ant.

You will need to compile this example first:
  mvn compile

To run the example type
  mvn jetty:run

The webservice will be listed in this overview:
  http://localhost:9080/webservices/


To run the example with Ant
  a. You need to have Apache CXF, Spring and Jetty installed. They can be
  downloaded from the following locations
    Apache CXF
    http://cxf.apache.org/
    Spring
    http://www.springframework.org/download
    Jetty
    http://www.eclipse.org/jetty/

  b. Export / Set home directories for the above as follows
    UNIX
    export CXF_HOME=<path to CXF install directory>
    export SPRING_HOME=<path to Spring install directory>
    export JETTY_HOME=<path to Jetty install directory>
    Windows
    set CXF_HOME=<path to CXF install directory>
    set SPRING_HOME=<path to Spring install directory>
    set JETTY_HOME=<path to Jetty install directory>

  c. To Run using Ant, type
    ant run

The webservice WSDL is exposed at:
  http://localhost:9080/webservices/incident?wsdl

To stop the example hit ctrl + c


For the latest & greatest documentation on how to use this example please see
  http://camel.apache.org/tutorial-example-reportincident.html

If you hit any problems please let us know on the Camel Forums
  http://camel.apache.org/discussion-forums.html

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!

------------------------
The Camel riders!



