## PetStore Example

This is an example that uses the `petstore` Camel connectors.

### How to run

This example can be run from the command line using:

    mvn spring-boot:run
    
### Scheduled connector

The `petstore` connector is a scheduled connector which means it has built-in
a Camel `timer` endpoint as the starting point, so you start from the connector
in a Camel route as shown below:

```
    from("petstore?operationId=getInventory&schedulerPeriod=2000")
        .log("Pets in the store ${body}");

```

What happens is that Camel will transform this into a route that looks like:

```
    from("timer:petstore?period=2000")
        .to("petstore?operationId=getInventory")
        .log("Pets in the store ${body}");

```