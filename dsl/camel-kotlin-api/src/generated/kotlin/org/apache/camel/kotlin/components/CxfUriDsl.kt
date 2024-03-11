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
 * Expose SOAP WebServices using Apache CXF or connect to external WebServices using CXF WS client.
 */
public fun UriDsl.cxf(i: CxfUriDsl.() -> Unit) {
  CxfUriDsl(this).apply(i)
}

@CamelDslMarker
public class CxfUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cxf")
  }

  private var beanId: String = ""

  private var address: String = ""

  /**
   * To lookup an existing configured CxfEndpoint. Must used bean: as prefix.
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
   * The data type messages supported by the CXF endpoint.
   */
  public fun dataFormat(dataFormat: String) {
    it.property("dataFormat", dataFormat)
  }

  /**
   * The WSDL style that describes how parameters are represented in the SOAP body. If the value is
   * false, CXF will chose the document-literal unwrapped style, If the value is true, CXF will chose
   * the document-literal wrapped style
   */
  public fun wrappedStyle(wrappedStyle: String) {
    it.property("wrappedStyle", wrappedStyle)
  }

  /**
   * The WSDL style that describes how parameters are represented in the SOAP body. If the value is
   * false, CXF will chose the document-literal unwrapped style, If the value is true, CXF will chose
   * the document-literal wrapped style
   */
  public fun wrappedStyle(wrappedStyle: Boolean) {
    it.property("wrappedStyle", wrappedStyle.toString())
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
   * Configure a cookie handler to maintain a HTTP session
   */
  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  /**
   * This option will set the default operationName that will be used by the CxfProducer which
   * invokes the remote service.
   */
  public fun defaultOperationName(defaultOperationName: String) {
    it.property("defaultOperationName", defaultOperationName)
  }

  /**
   * This option will set the default operationNamespace that will be used by the CxfProducer which
   * invokes the remote service.
   */
  public fun defaultOperationNamespace(defaultOperationNamespace: String) {
    it.property("defaultOperationNamespace", defaultOperationNamespace)
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
   * Which kind of operation that CXF endpoint producer will invoke
   */
  public fun wrapped(wrapped: String) {
    it.property("wrapped", wrapped)
  }

  /**
   * Which kind of operation that CXF endpoint producer will invoke
   */
  public fun wrapped(wrapped: Boolean) {
    it.property("wrapped", wrapped.toString())
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
   * This option controls whether the CXF component, when running in PAYLOAD mode, will DOM parse
   * the incoming messages into DOM Elements or keep the payload as a javax.xml.transform.Source object
   * that would allow streaming in some cases.
   */
  public fun allowStreaming(allowStreaming: String) {
    it.property("allowStreaming", allowStreaming)
  }

  /**
   * This option controls whether the CXF component, when running in PAYLOAD mode, will DOM parse
   * the incoming messages into DOM Elements or keep the payload as a javax.xml.transform.Source object
   * that would allow streaming in some cases.
   */
  public fun allowStreaming(allowStreaming: Boolean) {
    it.property("allowStreaming", allowStreaming.toString())
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
   * To use a custom CxfBinding to control the binding between Camel Message and CXF Message.
   */
  public fun cxfBinding(cxfBinding: String) {
    it.property("cxfBinding", cxfBinding)
  }

  /**
   * This option could apply the implementation of
   * org.apache.camel.component.cxf.CxfEndpointConfigurer which supports to configure the CXF endpoint
   * in programmatic way. User can configure the CXF server and client by implementing
   * configure{ServerClient} method of CxfEndpointConfigurer.
   */
  public fun cxfConfigurer(cxfConfigurer: String) {
    it.property("cxfConfigurer", cxfConfigurer)
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
   * Whether to merge protocol headers. If enabled then propagating headers between Camel and CXF
   * becomes more consistent and similar. For more details see CAMEL-6393.
   */
  public fun mergeProtocolHeaders(mergeProtocolHeaders: String) {
    it.property("mergeProtocolHeaders", mergeProtocolHeaders)
  }

  /**
   * Whether to merge protocol headers. If enabled then propagating headers between Camel and CXF
   * becomes more consistent and similar. For more details see CAMEL-6393.
   */
  public fun mergeProtocolHeaders(mergeProtocolHeaders: Boolean) {
    it.property("mergeProtocolHeaders", mergeProtocolHeaders.toString())
  }

  /**
   * To enable MTOM (attachments). This requires to use POJO or PAYLOAD data format mode.
   */
  public fun mtomEnabled(mtomEnabled: String) {
    it.property("mtomEnabled", mtomEnabled)
  }

  /**
   * To enable MTOM (attachments). This requires to use POJO or PAYLOAD data format mode.
   */
  public fun mtomEnabled(mtomEnabled: Boolean) {
    it.property("mtomEnabled", mtomEnabled.toString())
  }

  /**
   * To set additional CXF options using the key/value pairs from the Map. For example to turn on
   * stacktraces in SOAP faults, properties.faultStackTraceEnabled=true
   */
  public fun properties(properties: String) {
    it.property("properties", properties)
  }

  /**
   * Enable schema validation for request and response. Disabled by default for performance reason
   */
  public fun schemaValidationEnabled(schemaValidationEnabled: String) {
    it.property("schemaValidationEnabled", schemaValidationEnabled)
  }

  /**
   * Enable schema validation for request and response. Disabled by default for performance reason
   */
  public fun schemaValidationEnabled(schemaValidationEnabled: Boolean) {
    it.property("schemaValidationEnabled", schemaValidationEnabled.toString())
  }

  /**
   * Sets whether SOAP message validation should be disabled.
   */
  public fun skipPayloadMessagePartCheck(skipPayloadMessagePartCheck: String) {
    it.property("skipPayloadMessagePartCheck", skipPayloadMessagePartCheck)
  }

  /**
   * Sets whether SOAP message validation should be disabled.
   */
  public fun skipPayloadMessagePartCheck(skipPayloadMessagePartCheck: Boolean) {
    it.property("skipPayloadMessagePartCheck", skipPayloadMessagePartCheck.toString())
  }

  /**
   * This option enables CXF Logging Feature which writes inbound and outbound SOAP messages to log.
   */
  public fun loggingFeatureEnabled(loggingFeatureEnabled: String) {
    it.property("loggingFeatureEnabled", loggingFeatureEnabled)
  }

  /**
   * This option enables CXF Logging Feature which writes inbound and outbound SOAP messages to log.
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
   * This option is used to set the basic authentication information of password for the CXF client.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * This option is used to set the basic authentication information of username for the CXF client.
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * The bindingId for the service model to use.
   */
  public fun bindingId(bindingId: String) {
    it.property("bindingId", bindingId)
  }

  /**
   * The endpoint name this service is implementing, it maps to the wsdl:portname. In the format of
   * ns:PORT_NAME where ns is a namespace prefix valid at this scope.
   */
  public fun portName(portName: String) {
    it.property("portName", portName)
  }

  /**
   * This option can override the endpointUrl that published from the WSDL which can be accessed
   * with service address url plus wsd
   */
  public fun publishedEndpointUrl(publishedEndpointUrl: String) {
    it.property("publishedEndpointUrl", publishedEndpointUrl)
  }

  /**
   * The class name of the SEI (Service Endpoint Interface) class which could have JSR181 annotation
   * or not.
   */
  public fun serviceClass(serviceClass: String) {
    it.property("serviceClass", serviceClass)
  }

  /**
   * The service name this service is implementing, it maps to the wsdl:servicename.
   */
  public fun serviceName(serviceName: String) {
    it.property("serviceName", serviceName)
  }

  /**
   * The location of the WSDL. Can be on the classpath, file system, or be hosted remotely.
   */
  public fun wsdlURL(wsdlURL: String) {
    it.property("wsdlURL", wsdlURL)
  }
}
