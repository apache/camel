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
- camel-caffeine-lrucache
- camel-cloud
- camel-core
- camel-management-api
- camel-management-impl
- camel-support
- camel-util
- camel-util-json
 
Maven users of Apache Camel can keep using the dependency *camel-core* which will have transitive dependency on all of its modules, and therefore no migration is needed.
However users whom wants to trim the size of the classes on the classpath, can use fine grained Maven dependency on only the modules needed.
You may find how to do that in the examples.

We have also modularized many of the core components and moved them out of `camel-core` to individual components:

- camel-bean
- camel-browse
- camel-controlbus
- camel-dataformat
- camel-dataset
- camel-direct
- camel-directvm
- camel-file
- camel-language
- camel-log
- camel-mock
- camel-properties
- camel-ref
- camel-rest
- camel-saga
- camel-scheduler
- camel-seda
- camel-stub
- camel-timer
- camel-validator
- camel-vm
- camel-xpath
- camel-xslt


Migrating custom components
---------------------------

You should depend on `camel-support` and not `camel-core` directly.

The classes from `org.apache.camel.impl` that was intended to support Camel developers building custom components has been moved out of `camel-core` into `camel-support` into the `org.apache.camel.support` package. For example classes such as `DefaultComponent`, `DefaultEndpoint` etc has been moved and migration is necessary.

// TODO: Should we create a camel2-support JAR with an adapter to bridge between 2.x and 3.0

Migrating custom languages
--------------------------

The `LanguageAnnotation` annotation class has been moved from packge `org.apache.camel.language` to `org.apache.camel.support.language`.

Deprecated APIs and Components
------------------------------

All deprecated APIs and components from Camel 2.x has been removed in Camel 3.

Migrating Camel applications
----------------------------

### Removed components

We have removed all deprecated components from Camel 2.x, also including the old `camel-http` component. Instead you can use `camel-http4`.

### Renamed components

The `test` component has been renamed to `dataset-test` and moved out of `camel-core` into `camel-dataset` JAR.

We have also renamed `camel-jetty9` to `camel-jetty`.

### javax.script

The `camel-script` component has been removed and there is no support for javax.script, which is also deprecated in the JDK and to be removed from Java 11 onwards.

### Mock component

The `mock` component has been moved out of `camel-core` and as part of this work, we had to remove a number of methods on its _assertion clause builder_ that were seldom in use. Also we had to remove a few methods on `NotifyBuilder` that were using the `mock` component.

### ActiveMQ

If you are using the `activemq-camel` component, then you should migrate to use `camel-activemq` component, where the component name has changed from `org.apache.activemq.camel.component.ActiveMQComponent` to `org.apache.camel.component.activemq.ActiveMQComponent`.

### AWS 

The component `camel-aws` has been splitted in multiple components:

- camel-aws-cw
- camel-aws-ddb (which contains both ddb and ddbstreams components)
- camel-aws-ec2
- camel-aws-ecs
- camel-aws-eks
- camel-aws-iam
- camel-aws-kinesis (which contains both kinesis and kinesis-firehose components)
- camel-aws-kms
- camel-aws-lambda
- camel-aws-mq
- camel-aws-s3
- camel-aws-sdb
- camel-aws-ses
- camel-aws-sns
- camel-aws-sqs
- camel-aws-swf

So you'll have to add explicitly the dependencies for these components. From the OSGi perspective, there is still a `camel-aws` Karaf feature, which includes all the components features.

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


### Main class

The `Main` class from `camel-core`, `camel-spring` and `camel-cdi` has been modified to only support a single `CamelContext` which was really its intention, but there was some old crufy code for multiple Camels. The method `getCamelContextMap` has been removed, and there is just a `getCamelContext` method now.

### POJO annotations 

The `ref` attribute on `@Consume`, `@Produce` and `@EndpointInject` has been removed. Instead use the ref component in the `uri` attribute, eg `uri = "ref:myName"`.

The uri attribute has been deprecated, instead use value, which allows a shorthand style, from using `@Consume(uri = "jms:cheese")` to `@Consume("jms:cheese")`.

### Routes with multiple inputs

In Camel 2.x you could have 2 or more inputs to Camel routes, however this was not supported in all use-cases in Camel, and this functionality is seldom in use. This has
also been deprecated in Camel 2.x. In Camel 3 we have removed the remaining code for specifying multipe inputs to routes, and its now only possible to specify exactly only 1 input to a route.

### JSon DataFormat

The default JSon library with the JSon dataformat has changed from `XStream` to `Jackson`.

### Simple language

The functionality to change the simple language tokens for start/end functions has been removed. The default tokens with `${xxx}` and `$simple{xxx}` is now hardcoded (optimized). The functionality to change these tokens was never really in use and would only confuse Camel users if a new syntax are in use.

### Moved APIs

The following API changes may affect your existing Camel applications, which needs to be migrated.

TODO: Should this be a table?
TODO: Add the other moved classes/packages etc

#### CamelContext

The methods on `CamelContext` that are related to catalog has been moved into a new `CatalogCamelContext` interface, which you can access by adapting:

    CatalogCamelContext ccc = context.adapt(CatalogCamelContext.class);

The `loadRouteDefinitions` and `loadRestDefinitions` on `ModelCamelContext` has been changed to `addRouteDefinitions` and `addRestDefinitions` to be aligned with the other methods. You can find loader methods on the `ModelHelper` utility class.


#### Generic Information

The class `SimpleRegistry` is moved from `org.apache.camel.impl` to `org.apache.camel.support`. Also you should favour using the `org.apache.camel.support.DefaultRegistry` instead. Also you should use the `bind` operation instead of `put` to add entries to the `SimpleRegistry` or `DefaultRegistry`.

The class `CompositeRegistry` and `PropertyPlaceholderDelegateRegistry` has been deleted. Instead use `DefaultRegistry`.

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

The class `org.apache.camel.management.JmxSystemPropertyKeys` has been moved to `org.apache.camel.api.management.JmxSystemPropertyKeys`.

The class `org.apache.camel.builder.xml.XPathBuilder` has been moved to `org.apache.camel.language.xpath.XPathBuilder` and in the `camel-xpath` JAR.

The annotation `org.apache.camel.language.XPath` has been moved to `org.apache.camel.language.xpath.XPath` and in the `camel-xpath` JAR.

The exception `org.apache.camel.builder.xml.InvalidXPathExpression` has been renamed to `org.apache.camel.language.xpath.InvalidXPathException` and in the `camel-xpath` JAR.

The annotation `org.apache.camel.language.Bean` has been moved to `org.apache.camel.language.bean.Bean` and in the `camel-bean` JAR.

The annotation `org.apache.camel.language.Simple` has been moved to `org.apache.camel.language.simple.Simple`.

The annotation `org.apache.camel.Constant` has been removed, use `@Simple` instead.

The annotation `org.apache.camel.language.SpEL` has been moved to `org.apache.camel.language.spel.SpEL` and in the `camel-spring` JAR.

#### camel-test

If you are using camel-test and override the `createRegistry` method, for example to register beans from the `JndiRegisty` class, then this is no longer necessary, and instead
you should just use the `bind` method from the `Registry` API which you can call directly from `CamelContext`, such as:

    context.getRegistry().bind("myId", myBean);
    

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

The class `org.apache.camel.impl.RoutePolicySupport` has been moved to `org.apache.camel.support.RoutePolicySupport`. The return type from `startConsumer` and `stopConsumer` has been changed from `boolean` to `void` as they always returned `true` before.

The class `org.apache.camel.impl.ThrottlingInflightRoutePolicy` has been moved to `org.apache.camel.throttling.ThrottlingInflightRoutePolicy`


#### Aggregation

The class `XsltAggregationStrategy` has been moved from `org.apache.camel.builder.XsltAggregationStrategy` in the camel-core JAR to `org.apache.camel.component.xslt.XsltAggregationStrategy` and moved to the `camel-xslt` JAR.

When using the option `groupedExchange` on the aggregator EIP then the output of the aggregation is now longer also stored in the exchange property `Exchange.GROUPED_EXCHANGE`. This behaviour was already deprecated from Camel 2.13 onwards.

### Fallback type converters

The `@FallbackConverter` annotation has been removed, and you should use `@Converter(fallback = true)` instead. Also you can set `@Converter(loader = true)` on the converter class to allow Camel to generate source code for loading type converters in a faster way.


### Removed JMX APIs for explaining EIPs, components, etc.

The APIs that could find, and explain EIPs, components, endpoints etc has been removed. These APIs have little value for production runtimes, and you can obtain this kind of information via the `camel-catalog`. Also the related Camel Karaf commands that used these APIs has been removed.


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

There is an issue with MDC logging and correctly transfering the Camel breadscrumb id's under certain situations with routing over asynchronous endpoints, due to the internal routing engine refactorings. This change also affects the `camel-zipkin` component, which may not correctly transfer the span id's when using MDC logging as well. 

The tracer feature does not work (the implementation from Camel 2.x was deprecated), and we plan to implement a new and improved tracer.


