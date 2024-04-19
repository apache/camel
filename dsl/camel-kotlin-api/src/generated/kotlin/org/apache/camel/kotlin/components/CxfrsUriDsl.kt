/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Expose JAX-RS REST services using Apache CXF or connect to external REST services using CXF REST
 * client.
 */
public fun UriDsl.cxfrs(i: CxfrsUriDsl.() -> Unit) {
  CxfrsUriDsl(this).apply(i)
}

@CamelDslMarker
public class CxfrsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cxfrs")
  }

  private var beanId: String = ""

  private var address: String = ""

  /**
   * To lookup an existing configured CxfRsEndpoint. Must used bean: as prefix.
   */
  public fun beanId(beanId: String) {
    this.beanId = beanId
    it.url("$beanId:$address")
  }

  /**
   * The service publish address.
   */
  public fun address(address: String) {
    this.address = address
    it.url("$beanId:$address")
  }

  /**
   * Set the feature list to the CxfRs endpoint.
   */
  public fun features(features: String) {
    it.property("features", features)
  }

  /**
   * This option is used to specify the model file which is useful for the resource class without
   * annotation. When using this option, then the service class can be omitted, to emulate
   * document-only endpoints
   */
  public fun modelRef(modelRef: String) {
    it.property("modelRef", modelRef)
  }

  /**
   * Set custom JAX-RS provider(s) list to the CxfRs endpoint. You can specify a string with a list
   * of providers to lookup in the registy separated by comma.
   */
  public fun providers(providers: String) {
    it.property("providers", providers)
  }

  /**
   * The resource classes which you want to export as REST service. Multiple classes can be
   * separated by comma.
   */
  public fun resourceClasses(resourceClasses: String) {
    it.property("resourceClasses", resourceClasses)
  }

  /**
   * Sets the locations of the schema(s) which can be used to validate the incoming XML or
   * JAXB-driven JSON.
   */
  public fun schemaLocations(schemaLocations: String) {
    it.property("schemaLocations", schemaLocations)
  }

  /**
   * This option controls whether the PhaseInterceptorChain skips logging the Fault that it catches.
   */
  public fun skipFaultLogging(skipFaultLogging: String) {
    it.property("skipFaultLogging", skipFaultLogging)
  }

  /**
   * This option controls whether the PhaseInterceptorChain skips logging the Fault that it catches.
   */
  public fun skipFaultLogging(skipFaultLogging: Boolean) {
    it.property("skipFaultLogging", skipFaultLogging.toString())
  }

  /**
   * Sets how requests and responses will be mapped to/from Camel. Two values are possible:
   * SimpleConsumer: This binding style processes request parameters, multiparts, etc. and maps them to
   * IN headers, IN attachments and to the message body. It aims to eliminate low-level processing of
   * org.apache.cxf.message.MessageContentsList. It also also adds more flexibility and simplicity to
   * the response mapping. Only available for consumers. Default: The default style. For consumers this
   * passes on a MessageContentsList to the route, requiring low-level processing in the route. This is
   * the traditional binding style, which simply dumps the org.apache.cxf.message.MessageContentsList
   * coming in from the CXF stack onto the IN message body. The user is then responsible for processing
   * it according to the contract defined by the JAX-RS method signature. Custom: allows you to specify
   * a custom binding through the binding option.
   */
  public fun bindingStyle(bindingStyle: String) {
    it.property("bindingStyle", bindingStyle)
  }

  /**
   * This option can override the endpointUrl that published from the WADL which can be accessed
   * with resource address url plus _wadl
   */
  public fun publishedEndpointUrl(publishedEndpointUrl: String) {
    it.property("publishedEndpointUrl", publishedEndpointUrl)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * The service beans (the bean ids to lookup in the registry) which you want to export as REST
   * service. Multiple beans can be separated by comma
   */
  public fun serviceBeans(serviceBeans: String) {
    it.property("serviceBeans", serviceBeans)
  }

  /**
   * Configure a cookie handler to maintain a HTTP session
   */
  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  /**
   * The hostname verifier to be used. Use the # notation to reference a HostnameVerifier from the
   * registry.
   */
  public fun hostnameVerifier(hostnameVerifier: String) {
    it.property("hostnameVerifier", hostnameVerifier)
  }

  /**
   * The Camel SSL setting reference. Use the # notation to reference the SSL Context.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * This option tells the CxfRsProducer to inspect return codes and will generate an Exception if
   * the return code is larger than 207.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  /**
   * This option tells the CxfRsProducer to inspect return codes and will generate an Exception if
   * the return code is larger than 207.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  /**
   * If it is true, the CxfRsProducer will use the HttpClientAPI to invoke the service. If it is
   * false, the CxfRsProducer will use the ProxyClientAPI to invoke the service
   */
  public fun httpClientAPI(httpClientAPI: String) {
    it.property("httpClientAPI", httpClientAPI)
  }

  /**
   * If it is true, the CxfRsProducer will use the HttpClientAPI to invoke the service. If it is
   * false, the CxfRsProducer will use the ProxyClientAPI to invoke the service
   */
  public fun httpClientAPI(httpClientAPI: Boolean) {
    it.property("httpClientAPI", httpClientAPI.toString())
  }

  /**
   * This option is used to tell CxfRsProducer to ignore the message body of the DELETE method when
   * using HTTP API.
   */
  public fun ignoreDeleteMethodMessageBody(ignoreDeleteMethodMessageBody: String) {
    it.property("ignoreDeleteMethodMessageBody", ignoreDeleteMethodMessageBody)
  }

  /**
   * This option is used to tell CxfRsProducer to ignore the message body of the DELETE method when
   * using HTTP API.
   */
  public fun ignoreDeleteMethodMessageBody(ignoreDeleteMethodMessageBody: Boolean) {
    it.property("ignoreDeleteMethodMessageBody", ignoreDeleteMethodMessageBody.toString())
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * This option allows you to configure the maximum size of the cache. The implementation caches
   * CXF clients or ClientFactoryBean in CxfProvider and CxfRsProvider.
   */
  public fun maxClientCacheSize(maxClientCacheSize: String) {
    it.property("maxClientCacheSize", maxClientCacheSize)
  }

  /**
   * This option allows you to configure the maximum size of the cache. The implementation caches
   * CXF clients or ClientFactoryBean in CxfProvider and CxfRsProvider.
   */
  public fun maxClientCacheSize(maxClientCacheSize: Int) {
    it.property("maxClientCacheSize", maxClientCacheSize.toString())
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * To use a custom CxfBinding to control the binding between Camel Message and CXF Message.
   */
  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  /**
   * To use a custom configured CXF Bus.
   */
  public fun bus(bus: String) {
    it.property("bus", bus)
  }

  /**
   * This option is used to set the CXF continuation timeout which could be used in CxfConsumer by
   * default when the CXF server is using Jetty or Servlet transport.
   */
  public fun continuationTimeout(continuationTimeout: String) {
    it.property("continuationTimeout", continuationTimeout)
  }

  /**
   * This option could apply the implementation of
   * org.apache.camel.component.cxf.jaxrs.CxfRsEndpointConfigurer which supports to configure the CXF
   * endpoint in programmatic way. User can configure the CXF server and client by implementing
   * configure{Server/Client} method of CxfEndpointConfigurer.
   */
  public fun cxfRsConfigurer(cxfRsConfigurer: String) {
    it.property("cxfRsConfigurer", cxfRsConfigurer)
  }

  /**
   * Will set the default bus when CXF endpoint create a bus by itself
   */
  public fun defaultBus(defaultBus: String) {
    it.property("defaultBus", defaultBus)
  }

  /**
   * Will set the default bus when CXF endpoint create a bus by itself
   */
  public fun defaultBus(defaultBus: Boolean) {
    it.property("defaultBus", defaultBus.toString())
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * When the option is true, Camel will perform the invocation of the resource class instance and
   * put the response object into the exchange for further processing.
   */
  public fun performInvocation(performInvocation: String) {
    it.property("performInvocation", performInvocation)
  }

  /**
   * When the option is true, Camel will perform the invocation of the resource class instance and
   * put the response object into the exchange for further processing.
   */
  public fun performInvocation(performInvocation: Boolean) {
    it.property("performInvocation", performInvocation.toString())
  }

  /**
   * When the option is true, JAXRS UriInfo, HttpHeaders, Request and SecurityContext contexts will
   * be available to custom CXFRS processors as typed Camel exchange properties. These contexts can be
   * used to analyze the current requests using JAX-RS API.
   */
  public fun propagateContexts(propagateContexts: String) {
    it.property("propagateContexts", propagateContexts)
  }

  /**
   * When the option is true, JAXRS UriInfo, HttpHeaders, Request and SecurityContext contexts will
   * be available to custom CXFRS processors as typed Camel exchange properties. These contexts can be
   * used to analyze the current requests using JAX-RS API.
   */
  public fun propagateContexts(propagateContexts: Boolean) {
    it.property("propagateContexts", propagateContexts.toString())
  }

  /**
   * This option enables CXF Logging Feature which writes inbound and outbound REST messages to log.
   */
  public fun loggingFeatureEnabled(loggingFeatureEnabled: String) {
    it.property("loggingFeatureEnabled", loggingFeatureEnabled)
  }

  /**
   * This option enables CXF Logging Feature which writes inbound and outbound REST messages to log.
   */
  public fun loggingFeatureEnabled(loggingFeatureEnabled: Boolean) {
    it.property("loggingFeatureEnabled", loggingFeatureEnabled.toString())
  }

  /**
   * To limit the total size of number of bytes the logger will output when logging feature has been
   * enabled and -1 for no limit.
   */
  public fun loggingSizeLimit(loggingSizeLimit: String) {
    it.property("loggingSizeLimit", loggingSizeLimit)
  }

  /**
   * To limit the total size of number of bytes the logger will output when logging feature has been
   * enabled and -1 for no limit.
   */
  public fun loggingSizeLimit(loggingSizeLimit: Int) {
    it.property("loggingSizeLimit", loggingSizeLimit.toString())
  }
}
