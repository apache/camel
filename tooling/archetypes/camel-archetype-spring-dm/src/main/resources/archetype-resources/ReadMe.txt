Camel Router Project for Spring-DM (OSGi)
=========================================

To build this project use

    mvn install

You can run this example from the command line using
the following maven goal:

    mvn camel:run

To deploy the example in OSGi. For example using Apache ServiceMix
or Apache Karaf. You will run the following command from its shell:

    osgi:install -s mvn:${groupId}/${artifactId}

For more help see the Apache Camel documentation

    http://camel.apache.org/

