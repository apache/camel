# FHIR Example - OSGi

### Introduction

This is an example application of the `camel-fhir` component that can be executed inside an OSGi container. We will be using Apache Karaf.

The example assumes you have a running FHIR server at your disposal.
You may use [hapi-fhir-jpa-server-example](https://github.com/jamesagnew/hapi-fhir/tree/master/hapi-fhir-jpaserver-example)

By default, the example uses `http://localhost:8080/hapi-fhir-jpaserver-example/baseDstu3` as the FHIR server URL, DSTU3 as the FHIR version and `target/work/fhir/input`
as the directory to look for HL7V2 patients.
However, you can edit the `org.apache.camel.example.fhir.osgi.configuration.cfg` file to override the defaults and provide your own configuration.

### Build

You will need to build this example first:

```sh
$ mvn install
```

### Run OSGi / Karaf

This example can be executed within Karaf 4.x

To run the example, from the command line:

1. In the Karaf install root directory, start Karaf:

    ```sh
    $ bin/karaf
    ```

2. Install the pre-requisites:

    ```sh
    karaf@root()> repo-add camel ${version}
    karaf@root()> feature:install camel-blueprint camel-hl7 camel-fhir
    ```
    
3. Copy the configuration file org.apache.camel.example.fhir.osgi.configuration.cfg to the etc/ directory of your Karaf installation:
    ```
    InstallDir/etc/org.apache.camel.example.fhir.osgi.configuration.cfg       
    ```
4. Then install and start the example:

    ```sh
    karaf@root()> install -s mvn:org.apache.camel.example/camel-example-fhir-osgi/${version}
    ```

When the Camel application runs, you should see a folder created under `work/fhir/input`. Copy the file `hl7v2.patient`
located in the `data` folder into it.

By tailing the log with:

    ```sh
    karaf@root()> log:tail
    ```

The following messages should be displayed:

    ```
    2018-07-17 17:02:35,590 | INFO  | nt Dispatcher: 1 | FhirContext                      | 52 - ca.uhn.hapi.fhir.hapi-fhir-base - 3.3.0 | Creating new FHIR context for FHIR version [DSTU3]
    2018-07-17 17:02:35,631 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Route: fhir-example-osgi started and consuming from: file://work/fhir/input
    2018-07-17 17:02:35,631 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Total 1 routes, of which 1 are started
    2018-07-17 17:02:35,632 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Apache Camel 3.0.0-SNAPSHOT (CamelContext: camel-fhir) started in 0.853 seconds
    2018-07-17 17:03:06,157 | INFO  | /work/fhir/input | fhir-example-osgi                | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Converting hl7v2.patient
    2018-07-17 17:03:06,577 | INFO  | /work/fhir/input | fhir-example-osgi                | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Inserting Patient: {"resourceType":"Patient","id":"100005056","name":[{"family":"Freeman","given":["Vincent"]}]}
    2018-07-17 17:03:08,829 | INFO  | d #2 - CamelFhir | fhir-example-osgi                | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Patient created successfully: true
    ```

Hit <kbd>ctrl</kbd>+<kbd>c</kbd> to exit the log command.

Camel commands can be used to gain some insights on the Camel context, e.g.:

- The `camel:context-list` displays the Camel context:

    ```
    karaf@root()> camel:context-list
     Context        Status              Total #       Failed #     Inflight #   Uptime        
     -------        ------              -------       --------     ----------   ------        
     camel-fhir     Started                   1              0              0   3 minute  
    ```

- The `camel:route-list` command displays the Camel routes:

    ```
    karaf@root()> camel:route-list
     Context        Route              Status              Total #       Failed #     Inflight #   Uptime     
     -------        -----              ------              -------       --------     ----------   ------      
     camel-fhir     fhir-example-osgi  Started                   1              0              0   4 minutes
    ```

- And the `camel:route-info` command displays route information:

    ```
    karaf@root()> camel:route-info camel-fhir fhir-example-osgi                                                                                                                                                        
    Camel Route fhir-example-osgi
        Camel Context: camel-fhir
        State: Started
        State: Started
    
    
    Statistics
        Exchanges Total: 1
        Exchanges Completed: 1
        Exchanges Failed: 0
        Exchanges Inflight: 0
        Min Processing Time: 2674 ms
        Max Processing Time: 2674 ms
        Mean Processing Time: 2674 ms
        Total Processing Time: 2674 ms
        Last Processing Time: 2674 ms
        Delta Processing Time: 2674 ms
        Start Statistics Date: 2018-07-17 17:02:35
        Reset Statistics Date: 2018-07-17 17:02:35
        First Exchange Date: 2018-07-17 17:03:08
        Last Exchange Date: 2018-07-17 17:03:08
    ```

Finally, you can stop the example with:

    ```sh
    karaf@root()> uninstall camel-example-fhir-osgi
    ```

And check in the log that the Camel context has been gracefully
shutdown:
    
    ```
    2018-07-17 17:09:32,418 | INFO  | xample-fhir-osgi | BlueprintExtender                | 12 - org.apache.aries.blueprint.core - 1.8.3 | Destroying BlueprintContainer for bundle org.apache.camel.example.camel-example-fhir-osgi/2.23.0.SNAPSHOT
    2018-07-17 17:09:32,420 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Stopping CamelContext: camel-fhir
    2018-07-17 17:09:32,421 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Apache Camel 3.0.0-SNAPSHOT (CamelContext: camel-fhir) is shutting down
    2018-07-17 17:09:32,423 | INFO  | nt Dispatcher: 1 | DefaultShutdownStrategy          | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Starting to graceful shutdown 1 routes (timeout 300 seconds)
    2018-07-17 17:09:32,427 | INFO  | 3 - ShutdownTask | DefaultShutdownStrategy          | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Route: fhir-example-osgi shutdown complete, was consuming from: file://work/fhir/input
    2018-07-17 17:09:32,428 | INFO  | nt Dispatcher: 1 | DefaultShutdownStrategy          | 61 - org.apache.camel.camel-core - 2.23.0.SNAPSHOT | Graceful shutdown of 1 routes completed in 0 seconds
    2018-07-17 17:09:32,438 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Apache Camel 3.0.0-SNAPSHOT (CamelContext: camel-fhir) uptime 6 minutes
    2018-07-17 17:09:32,438 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 59 - org.apache.camel.camel-blueprint - 2.23.0.SNAPSHOT | Apache Camel 3.0.0-SNAPSHOT (CamelContext: camel-fhir) is shutdown in 0.017 seconds
    ```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
