# Camel Olingo4 OSGI Example using Blueprint

### Introduction

This example shows how to use the camel-olingo4 component in an OSGI environment. We will use the
the sample OData 4.0 remote TripPinservice published on http://services.odata.org/TripPinRESTierService by creating
two People who's data are loaded from a directory.

#### OSGi / Karaf

This example can be executed within Karaf 4.x. and relies on the ```camel-olingo4``` feature.

To run the example, from the command line:

1. In the Karaf install root directory, start Karaf:

    ```sh
    $ bin/karaf
    ```

2. Install the pre-requisites:

    ```sh
    karaf@root()> repo-add camel ${version}
    karaf@root()> feature:install camel-blueprint camel-olingo4
    ```

3. Then install and start the example:

    ```sh
    karaf@root()> install -s mvn:org.apache.camel.example/camel-example-olingo4-blueprint/${version}
    ```
4. Copy the files found in ```src/main/resources``` to the ```work/odata/input``` in the karaf install
   root directory created by the camel route. 

By tailing the log with:

```sh
karaf@root()> log:tail
```

The following messages should be displayed:

```
2017-11-29 15:46:22,524 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Apache Camel 2.21.0-SNAPSHOT (CamelContext: odata4-example-context) started in 0.102 seconds
2017-11-29 15:46:23,528 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Receiving file person2json
2017-11-29 15:46:23,528 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Sending file person2json to OData Test Service
2017-11-29 15:46:24,317 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Receiving file person1.json
2017-11-29 15:46:24,317 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Sending file person1.json to OData Test Service
2017-11-29 15:46:24,665 | INFO  | I/O dispatcher 1 | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Done creating person with properties [ClientPropertyImpl{name=UserName, value=jdoe, annotations=[]}, ClientPropertyImpl{name=FirstName, value=John, annotations=[]}, ClientPropertyImpl{name=LastName, value=Doe, annotations=[]}, ClientPropertyImpl{name=MiddleName, value=, annotations=[]}, ClientPropertyImpl{name=Gender, value=Male, annotations=[]}, ClientPropertyImpl{name=Age, value=, annotations=[]}, ClientPropertyImpl{name=Emails, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=FavoriteFeature, value=Feature1, annotations=[]}, ClientPropertyImpl{name=Features, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=AddressInfo, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=HomeAddress, value=, annotations=[]}]
2017-11-29 15:46:24,689 | INFO  | I/O dispatcher 2 | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Done creating person with properties [ClientPropertyImpl{name=UserName, value=jmorrow, annotations=[]}, ClientPropertyImpl{name=FirstName, value=Jerome, annotations=[]}, ClientPropertyImpl{name=LastName, value=Morrow, annotations=[]}, ClientPropertyImpl{name=MiddleName, value=, annotations=[]}, ClientPropertyImpl{name=Gender, value=Male, annotations=[]}, ClientPropertyImpl{name=Age, value=, annotations=[]}, ClientPropertyImpl{name=Emails, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=FavoriteFeature, value=Feature1, annotations=[]}, ClientPropertyImpl{name=Features, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=AddressInfo, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=HomeAddress, value=, annotations=[]}]

```

You can uninstall the example with:

```sh
karaf@root()> uninstall camel-example-olingo4-blueprint
```

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
