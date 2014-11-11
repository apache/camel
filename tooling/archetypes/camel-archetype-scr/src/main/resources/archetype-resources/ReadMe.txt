Camel SCR bundle project
========================

To build this project run

    mvn install

To deploy this project in Apache Karaf (2.4.0)

    On Karaf command line:

    # Add Camel feature repository
    features:chooseurl camel ${archetypeVersion}

    # Install camel-scr feature
    features:install camel-scr

    # Install commons-lang, used in the example route to validate parameters
    osgi:install mvn:commons-lang/commons-lang/2.6

    # Install and start your bundle
    osgi:install -s mvn:${groupId}/${artifactId}/${version}

    # See how it's running
    log:tail -n 10

    Press ctrl-c to stop watching the log.

For more help see the Apache Camel documentation

    http://camel.apache.org/
