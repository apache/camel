Camel Router WAR Project
========================

This project includes a sample route as as a WAR.
You can build the WAR by running

    mvn install

You can then run the project by dropping the WAR into your 
favorite web container or just run

    mvn jetty:run

to start up and deploy to Jetty.

If you have JBoss AS running you can deploy using

   mvn jboss-as:deploy

Or to redeploy

    mvn jboss-as:redeploy


The application will be available at:
   http://localhost:8080/${artifactId}/

For more help see the Apache Camel documentation

    http://camel.apache.org/

