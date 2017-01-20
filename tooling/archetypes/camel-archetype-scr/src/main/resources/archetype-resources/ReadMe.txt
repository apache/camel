Camel SCR bundle project
========================

To build this project run

    mvn install

To deploy this project in Apache Karaf

On Karaf command line:

    feature:repo-add camel ${camel-version}
    feature:install camel-scr
    osgi:install -s mvn:${groupId}/${artifactId}/${version}

See how it's running

    log:tail

Press `ctrl-c` to stop watching the log.

For more help see the Apache Camel documentation

    http://camel.apache.org/
