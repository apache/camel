Apache Camel 3 Migration Guide
==============================

This document is intended for helping you migrate your Apache Camel applications
from version 2.x to 3.0.

Before you start
----------------

// TODO when we drop Java8
// TODO Apache Camel 3 requires Java 11. Java 8 are no longer supported.

Modularization of camel-core
----------------------------

One of the biggest change is the modularization of camel-core.
In Camel 2.x camel-core was one JAR file, which now has been splitup into many JARs as follows:

- camel-api
- camel-base
- camel-core
- camel-management-api
- camel-management-impl
- camel-support
- camel-util
 
Maven users of Apache Camel can keep using the dependency *camel-core* which will have transitive dependency on all of its modules, and therefore no migration is needed.
However users whom wants to trim the size of the classes on the classpath, can use fine grained Maven dependency on only the modules needed.
You may find how to do that in the examples.

We have also modularized many of the core components and moved them out of `camel-core` to individual components:

- camel-browse
- camel-controlbus
- camel-dataformat
- camel-direct
- camel-directvm
- camel-file
- camel-language
- camel-log
- camel-properties
- camel-ref
- camel-rest
- camel-saga
- camel-scheduler
- camel-seda
- camel-stub
- camel-timer
- camel-validator
- camel-xslt

TODO: there are some remainder components in camel-core we would like to modularize
TODO: we need camel-core-minimal dependency for just basic Camel


Migrating custom components
---------------------------

You should depend on `camel-support` and not `camel-core` directly.

The classes from `org.apache.camel.impl` that was intended to support Camel developers building custom components has been moved out of `camel-core` into `camel-support` into the `org.apache.camel.support` package. For example classes such as `DefaultComponent`, `DefaultEndpoint` etc has been moved and migration is nessasary.

// TODO: Should we create a camel2-support JAR with an adapter to bridge between 2.x and 3.0

Deprecated APIs and Components
------------------------------

All deprecated APIs and components from Camel 2.x has been removed in Camel 3.

Migrating Camel applications
----------------------------

### Removed components

We have removed all deprecated components from Camel 2.x, also including the old `camel-http` component. Instead you can use `camel-http4`.

We have also renamed `camel-jetty9` to `camel-jetty`.

### ActiveMQ

If you are using the `activemq-camel` component, then you should migrate to use `camel-activemq` component, where the component name has changed from `org.apache.activemq.camel.component.ActiveMQComponent` to `org.apache.camel.component.activemq.ActiveMQComponent`.

### JMX

If you run Camel standalone with just `camel-core` as dependency, and you want JMX enabled out of the box, then you need to add `camel-management-impl` as dependency.

For using `ManagedCamelContext` you now need to get this an extension from `CamelContext` as follows:

    ManagedCamelContext managed = camelContext.getExtension(ManagedCamelContext.class);

### Configuring global options on CamelContext

In Camel 2.x we have deprecated `getProperties` on `CamelContext` in favour of `getGlobalOptions`, so you should migrate to:

    context.getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
    context.getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");

... and in XML:

    <globalOptions>
      <globalOption key="CamelJacksonEnableTypeConverter" value="true"/>
      <globalOption key="CamelJacksonTypeConverterToPojo" value="true"/>
    </globalOptions>


### Moved APIs

The following API changes may affect your existing Camel applications, which needs to be migrated.

TODO: Should this be a table?
TODO: Add the other moved classes/packages etc

#### Generic Information

The classes from `org.apache.camel.impl` that was intended to support Camel developers building custom components has been moved out of `camel-core` into `camel-support` into the `org.apache.camel.support` package. If you have built custom Camel components that may have used some of these APIs you would then need to migrate.

All the classes in `org.apache.camel.util.component` has been moved from the camel-core JAR to the package `org.apache.camel.support.component` in the `camel-support` JAR.

The method `xslt` has been removed from `org.apache.camel.builder.AggregationStrategies`. Instead use the `XsltAggregationStrategy` from `camel-xslt` JAR directly.

The getter/setter for `bindingMode` on `RestEndpoint` has been changed to use type `org.apache.camel.spi.RestConfiguration.RestBindingMode` from `camel-api` JAR. Instead of using this type class you can also call the setter method with string type instead.

The `activemq-camel` component has been moved from ActiveMQ into Camel and it is now called `camel-activemq`, the package has been changed accordingly to `org.apache.camel.component.activemq`

The method `includeRoutes` on `RouteBuilder` has been removed. This functionality was not fully in use and was deprecated in Camel 2.x.

The exception `PredicateValidationException` has been moved from package `org.apache.camel.processor.validation` to `org.apache.camel.support.processor.validation.PredicateValidationException`.

The class `org.apache.camel.util.toolbox.AggregationStrategies` has been moved to `org.apache.camel.builder.AggregationStrategies`.

The class `org.apache.camel.processor.aggregate.AggregationStrategy` has been moved to `org.apache.camel.AggregationStrategy`.

The class `org.apache.camel.processor.loadbalancer.SimpleLoadBalancerSupport` has been removed, instead use `org.apache.camel.processor.loadbalancer.LoadBalancerSupport`.

#### Controlling routes

The `startRoute`, `stopRoute`, `suspendRoute`, `resumeRoute`, `getRouteStatus`, and other related methods on `CamelContext` has been moved to the `RouteController` as shown below:

    context.getRouteController().startRoute("myRoute");


#### JMX events

All the events from package `org.apache.camel.management.event` has been moved to the class `org.apache.camel.spi.CamelEvent` as sub-classes, for example the event for CamelContext started would be `CamelEvent.CamelContextStartedEvent`.

#### AdviceWith

Testing using `adviceWith` currently needs to be changed from:

    context.getRouteDefinition("start").adviceWith(context, new RouteBuilder() {
       ...
    }

to using style:

    RouteReifier.adviceWith(context.getRouteDefinition("start"), context, new RouteBuilder() {
        ...
    }

We are planning on making this easier in 3.0.0-M2 onwards.

#### Generic Classes

The class `JNDIContext` has been moved from `org.apache.camel.util.jndi.JNDIContext` in the camel-core JAR to `org.apache.camel.support.jndi.JNDIContext` and moved to the `camel-support` JAR.

#### EIPs

The `circuitBreaker` load-balancer EIP was deprecated in Camel 2.x, and has been removed. Instead use Hystrix EIP as the load-balancer.

The class `ThreadPoolRejectedPolicy` has been moved from `org.apache.camel.ThreadPoolRejectedPolicy` to `org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy`.

#### Languages

The simple language `property` function was deprecated in Camel 2.x and has been removed. Use `exchangeProperty` as function name.

The terser language has been renamed from terser to hl7terser.

#### JSSE

The classes from `org.apache.camel.util.jsse` has been moved to `org.apache.camel.support.jsse`.

#### Helpers and support

The class `AsyncProcessorHelper` has been moved from `org.apache.camel.util.AsyncProcessorHelper` in the camel-core JAR to `org.apache.camel.support.AsyncProcessorHelper` and moved to the `camel-support` JAR.

The class `AsyncProcessorConverterHelper` has been moved from `org.apache.camel.util.AsyncProcessorConverterHelper` in the camel-core JAR to `org.apache.camel.support.AsyncProcessorConverterHelper` and moved to the `camel-support` JAR.

The class `CamelContextHelper` has been moved from `org.apache.camel.util.CamelContextHelper` in the camel-core JAR to `org.apache.camel.support.CamelContextHelper` and moved to the `camel-support` JAR.

The class `EndpointHelper` has been moved from `org.apache.camel.util.EndpointHelper` in the camel-core JAR to `org.apache.camel.support.EndpointHelper` and moved to the `camel-support` JAR.

The class `EventHelper` has been moved from `org.apache.camel.util.EventHelper` in the camel-core JAR to `org.apache.camel.support.EventHelper` and moved to the `camel-support` JAR.

The class `ExchangeHelper` has been moved from `org.apache.camel.util.ExchangeHelper` in the camel-core JAR to `org.apache.camel.support.ExchangeHelper` and moved to the `camel-support` JAR.

The class `GZIPHelper` has been moved from `org.apache.camel.util.GZIPHelper` in the camel-core JAR to `org.apache.camel.support.GZIPHelper` and moved to the `camel-support` JAR.

The class `JsonSchemaHelper` has been moved from `org.apache.camel.util.JsonSchemaHelper` in the camel-core JAR to `org.apache.camel.support.JsonSchemaHelper` and moved to the `camel-support` JAR.

The class `MessageHelper` has been moved from `org.apache.camel.util.MessageHelper` in the camel-core JAR to `org.apache.camel.support.MessageHelper` and moved to the `camel-support` JAR.

The class `ObjectHelper` has been moved from `org.apache.camel.util.ObjectHelper` in the camel-core JAR and splitted into `org.apache.camel.support.ObjectHelper` and moved to the `camel-support` JAR and into `org.apache.camel.util.ObjectHelper` and moved to the `camel-util` JAR. This has been done to isolate the methods using `camel-api` JAR: those method are in the `camel-support` JAR, the other in the `camel-util` JAR.

The class `PlatformHelper` has been moved from `org.apache.camel.util.PlatformHelper` in the camel-core JAR to `org.apache.camel.support.PlatformHelper` and moved to the `camel-support` JAR.

The class `PredicateAssertHelper` has been moved from `org.apache.camel.util.PredicateAssertHelper` in the camel-core JAR to `org.apache.camel.support.PredicateAssertHelper` and moved to the `camel-support` JAR.

The class `ResolverHelper` has been moved from `org.apache.camel.util.ResolverHelper` in the camel-core JAR to `org.apache.camel.support.ResolverHelper` and moved to the `camel-support` JAR.

The class `ResourceHelper` has been moved from `org.apache.camel.util.ResourceHelper` in the camel-core JAR to `org.apache.camel.support.ResourceHelper` and moved to the `camel-support` JAR.

The class `RestProducerFactoryHelper` has been moved from `org.apache.camel.spi.RestProducerFactoryHelper` in the camel-core JAR to `org.apache.camel.support.RestProducerFactoryHelper` and moved to the `camel-support` JAR.

The class `ServiceHelper` has been moved from `org.apache.camel.util.ServiceHelper` in the camel-core JAR to `org.apache.camel.support.service.ServiceHelper` and moved to the `camel-api` JAR.

The class `UnitOfWorkHelper` has been moved from `org.apache.camel.util.UnitOfWorkHelper` in the camel-core JAR to `org.apache.camel.support.UnitOfWorkHelper` and moved to the `camel-support` JAR.


#### Idempotent Repositories

The class `FileIdempotentRepository` has been moved from `org.apache.camel.processor.idempotent.FileIdempotentRepository` in the camel-core JAR to `org.apache.camel.support.processor.idempotent.FileIdempotentRepository` and moved to the `camel-suppor` JAR.

The class `MemoryIdempotentRepository` has been moved from `org.apache.camel.processor.idempotent.MemoryIdempotentRepository` in the camel-core JAR to `org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository` and moved to the `camel-suppor` JAR.


#### Route Policies

The class `org.apache.camel.impl.RoutePolicySupport` has been moved to `org.apache.camel.support.RoutePolicySupport`.

The class `org.apache.camel.impl.ThrottlingInflightRoutePolicy` has been moved to `org.apache.camel.throttling.ThrottlingInflightRoutePolicy`


#### Aggregation

The class `XsltAggregationStrategy` has been moved from `org.apache.camel.builder.XsltAggregationStrategy` in the camel-core JAR to `org.apache.camel.component.xslt.XsltAggregationStrategy` and moved to the `camel-xslt` JAR.

When using the option `groupedExchange` on the aggregator EIP then the output of the aggregation is now longer also stored in the exchange property `Exchange.GROUPED_EXCHANGE`. This behaviour was already deprecated from Camel 2.13 onwards.

### Other changes

The default for use breadcrumbs has been changed from `true` to `false`.


### XML DSL Migration

The XML DSL has been changed slightly.

The custom load balancer EIP has changed from `<custom>` to `<customLoadBalancer>` 

The XMLSecurity data format has renamed the attribute `keyOrTrustStoreParametersId` to `keyOrTrustStoreParametersRef` in the `<secureXML>` tag.

The `<zipFile>` data format has been renamed to `<zipfile>`.


Migrating Camel Maven Plugins
-----------------------------

The `camel-maven-plugin` has been splitup into two maven plugins:

- camel-maven-plugin
- camel-report-maven-plugin

The former has the `run` goal, which is intended for quickly running Camel applications standalone.

The `camel-report-maven-plugin` has the `validate` and `route-coverage` goals which is used for generating reports of your Camel projects such as validating Camel endpoint URIs and route coverage reports, etc.


Known Issues
------------

There is an issue with MDC logging and correctly transfering the Camel breadscrumb id's under certain situations with routing over asynchronous endpoints, due to the internal routing engine refactorings. This change also affects the `camel-zipkin` component, with may not correctly transfer the span id's when using MDC logging as well. 

The tracer feature does not work (the implementation from Camel 2.x was deprecated), and we plan to implemented a new and improved tracer.


