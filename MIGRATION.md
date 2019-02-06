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

TODO: we need camel-core-minimal dependency for just basic Camel


Migrating custom components
---------------------------

You should depend on `camel-support` and not `camel-core` directly.

The classes from `org.apache.camel.impl` that was intended to support Camel developers building custom components has been moved out of `camel-core` into `camel-support` into the `org.apache.camel.support` package. For example classes such as `DefaultComponent`, `DefaultEndpoint` etc has been moved and migration is nessasary.

// TODO: Should we create a camel2-support JAR with an adapter to bridge between 2.x and 3.0

Deprecated APIs and Components
------------------------------

All deprecated APIs and components from Camel 2.x has been removed in Camel 3.

Migration Camel applications
----------------------------

The following API changes may affect your existing Camel applications, which needs to be migrated.

### Moved APIs

TODO: Should this be a table?
TODO: Add the other moved classes/packages etc

#### Generic Information

The classes from `org.apache.camel.impl` that was intended to support Camel developers building custom components has been moved out of `camel-core` into `camel-support` into the `org.apache.camel.support` package. If you have built custom Camel components that may have used some of these APIs you would then need to migrate.

All the classes in `org.apache.camel.util.component` has been moved from the camel-core JAR to the package `org.apache.camel.support.component` in the `camel-support` JAR.

The method `xslt` has been removed from `org.apache.camel.builder.AggregationStrategies`. Instead use the `XsltAggregationStrategy` from `camel-xslt` JAR directly.

The getter/setter for `bindingMode` on `RestEndpoint` has been changed to use type `org.apache.camel.spi.RestConfiguration.RestBindingMode` from `camel-api` JAR. Instead of using this type class you can also call the setter method with string type instead.

The `activemq-camel` component has been moved from ActiveMQ into Camel and it is now called `camel-activemq`, the package has been changed accordingly to `org.apache.camel.component.activemq`

The method `includeRoutes` on `RouteBuilder` has been removed. This functionality was not fully in use and was deprecated in Camel 2.x.

#### Generic Classes

The class `JNDIContext` has been moved from `org.apache.camel.util.jndi.JNDIContext` in the camel-core JAR to `org.apache.camel.support.jndi.JNDIContext` and moved to the `camel-support` JAR.

#### Helpers

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

#### Aggregation

The class `XsltAggregationStrategy` has been moved from `org.apache.camel.builder.XsltAggregationStrategy` in the camel-core JAR to `org.apache.camel.component.xslt.XsltAggregationStrategy` and moved to the `camel-xslt` JAR.

When using the option `groupedExchange` on the aggregator EIP then the output of the aggregation
is now longer also stored in the exchange property `Exchange.GROUPED_EXCHANGE`.
This behaviour was already deprecated from Camel 2.13 onwards.

### XML DSL Migration

The XML DSL has been changed slightly.

The custom load balancer EIP has changed from `<custom>` to `<customLoadBalancer>` 

The XMLSecurity data format has renamed the attribute `keyOrTrustStoreParametersId` to `keyOrTrustStoreParametersRef` in the `<secureXML>` tag.

The `<zipFile>` data format has been renamed to `<zipfile>`.