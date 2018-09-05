# Camel Spring Boot Metrics Example


### Introduction
This example sends Camel route metrics to Graphite from a Spring Boot app.

Please read **Configuration** first

### Build
You will need to compile this example first:

	mvn install

### Run
To run the example type

	java -jar target/camel-example-spring-boot-metrics.jar



You will see logging from the "Fast" and "Slow" routes, and metrics will be sent to Graphite (or nc) every 5 seconds.

### Configuration
Spring Boot auto-configures the `com.codahale.metrics.MetricRegistry`.  See code comments in `Application.java` for further details.

If you already have a Graphite server, make sure that UDP is enabled (set `ENABLE_UDP_LISTENER = True` in carbon.conf).

If Graphite is not on your local machine, replace `localhost` in `Application.java` with the hostname or IP address of your Graphite server.  

If you want to use TCP instead of UDP, use `com.codahale.metrics.graphite.Graphite` instead of `com.codahale.metrics.graphite.GraphiteUDP`,
as shown here: http://metrics.dropwizard.io/3.1.0/manual/graphite/

If you can't be bothered to set up a Graphite server right now, you can simulate it by running `nc -ul 2003` on Linux.
If you don't have `nc`, use `yum search netcat` to find a suitable package to install (e.g. nmap-ncat.x86_64).

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
