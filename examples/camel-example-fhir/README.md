# FHIR Example - CDI

### Introduction

This is an example application of the `camel-fhir` component. We'll be using `camel-cdi` as well for an easy setup.

This example will read HL7V2 patients from a directory and convert them to FHIR dtsu3 patients and upload them to a configured FHIR server. 

The example assumes you have a running FHIR server at your disposal.
You may use [hapi-fhir-jpa-server-example](https://github.com/jamesagnew/hapi-fhir/tree/master/hapi-fhir-jpaserver-example)

By default, the example uses `http://localhost:8080/hapi-fhir-jpaserver-example/baseDstu3` as the FHIR server URL, DSTU3 as the FHIR version and `target/work/fhir/input`
as the directory to look for HL7V2 patients.
However, you can edit the `application.properties` file to override the defaults and provide your own configuration.

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ mvn camel:run
```

When the Camel application runs, you should see a folder created under `target/work/fhir/input`. Copy the file `hl7v2.patient`
located in the `data` folder into it. You should see the following output:
```
2018-07-04 16:22:52,189 [cdi.Main.main()] INFO  DefaultCamelContext            - Route: fhir-example started and consuming from: file://target/work/fhir/input
2018-07-04 16:22:52,189 [cdi.Main.main()] INFO  DefaultCamelContext            - Total 1 routes, of which 1 are started
2018-07-04 16:22:52,190 [cdi.Main.main()] INFO  DefaultCamelContext            - Apache Camel 2.22.0-SNAPSHOT (CamelContext: camel-example-fhir-cdi) started in 0.636 seconds
2018-07-04 16:22:52,203 [cdi.Main.main()] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container 357a3776-d8cd-40be-abb4-ad91a43c9755 initialized
2018-07-04 16:22:57,705 [work/fhir/input] INFO  fhir-example                   - Converting hl7v2.patient
2018-07-04 16:22:58,176 [work/fhir/input] INFO  fhir-example                   - Inserting Patient: {"resourceType":"Patient","id":"100005056","name":[{"family":"Freeman","given":["Vincent"]}]}
2018-07-04 16:22:58,669 [ #3 - CamelFhir] INFO  fhir-example                   - Patient creating successfully: true
```

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!