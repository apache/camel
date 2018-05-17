# Spring Java Config Example

### Introduction
This example shows how to work with Micrometer metrics, using Spring Java Config
to boot up Camel, configure the routes and meters.

The example triggers an exchange every 10s that runs through a route using a variable delay.
The exchange is measured in various ways:

* using Micrometer producers (timer and distribution summary)
* using a Route Policy Factory
* using Event Notifiers


### Build
You will need to compile this example first:

	mvn compile


### Setup of Monitoring backend

This example uses [Prometheus](https://prometheus.io) as monitoring backend.

* Download the package for your platform and unpack it on your local host
* Edit the `prometheus.yml` file and append another `scrape_config`:

```
...
  - job_name: 'camel'
    static_configs:
      - targets: ['localhost:8088']
```

* Start up Prometheus

Optionally, you can install a metrics visualizer, e.g. [Grafana](https://grafana.com/):

* Download the package for your platform and unpack it on your local host
* Add your Prometheus server as a data source
* Import a [suitable dashboard](https://grafana.com/dashboards/4701)
* Add some graphs to display Camel metrics (this is out of scope for this example).


### Run
To run the example type

	mvn exec:java


* You can access http://localhost:8088/metrics in order to manually obtain the Micrometer output for Prometheus.
* In Prometheus, you can [query](https://prometheus.io/docs/prometheus/latest/querying/examples/) for one of 
the metrics related to Camel or the JVM.

To stop the example hit <kbd>Ctrl</kbd>+<kbd>c</kbd>

### Configuration
You can see the routing rules by looking at the java code in the
`src/main/java directory`

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!



The Camel riders!
