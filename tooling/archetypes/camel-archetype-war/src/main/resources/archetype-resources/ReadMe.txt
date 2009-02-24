Camel Router WAR Project with Web Console and REST Support
==========================================================

This project bundles the Camel Web Console, REST API, and some
sample routes as a WAR. You can run this by dropping the WAR 
into your favorite web container or just run

mvn jetty:run-war

on the command line.


Web Console
===========

You can view the Web Console by pointing your browser to http://localhost:8080/

You should be able to do things like

    * browse the available endpoints
    * browse the messages on an endpoint if it is a BrowsableEndpoint
    * send a message to an endpoint
    * create new endpoints

For more help see the Apache Camel documentation

    http://camel.apache.org/
    
