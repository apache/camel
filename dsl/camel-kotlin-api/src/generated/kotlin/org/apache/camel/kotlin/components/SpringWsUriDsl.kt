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
 * Access external web services as a client or expose your own web services.
 */
public fun UriDsl.`spring-ws`(i: SpringWsUriDsl.() -> Unit) {
  SpringWsUriDsl(this).apply(i)
}

@CamelDslMarker
public class SpringWsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("spring-ws")
  }

  private var type: String = ""

  private var lookupKey: String = ""

  private var webServiceEndpointUri: String = ""

  /**
   * Endpoint mapping type if endpoint mapping is used. rootqname - Offers the option to map web
   * service requests based on the qualified name of the root element contained in the message.
   * soapaction - Used to map web service requests based on the SOAP action specified in the header of
   * the message. uri - In order to map web service requests that target a specific URI. xpathresult -
   * Used to map web service requests based on the evaluation of an XPath expression against the
   * incoming message. The result of the evaluation should match the XPath result specified in the
   * endpoint URI. beanname - Allows you to reference an
   * org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher object in order to integrate
   * with existing (legacy) endpoint mappings like PayloadRootQNameEndpointMapping,
   * SoapActionEndpointMapping, etc
   */
  public fun type(type: String) {
    this.type = type
    it.url("$type:$lookupKey:$webServiceEndpointUri")
  }

  /**
   * Endpoint mapping key if endpoint mapping is used
   */
  public fun lookupKey(lookupKey: String) {
    this.lookupKey = lookupKey
    it.url("$type:$lookupKey:$webServiceEndpointUri")
  }

  /**
   * The default Web Service endpoint uri to use for the producer.
   */
  public fun webServiceEndpointUri(webServiceEndpointUri: String) {
    this.webServiceEndpointUri = webServiceEndpointUri
    it.url("$type:$lookupKey:$webServiceEndpointUri")
  }

  /**
   * Option to provide a custom MessageFilter. For example when you want to process your headers or
   * attachments by your own.
   */
  public fun messageFilter(messageFilter: String) {
    it.property("messageFilter", messageFilter)
  }

  /**
   * Option to provide a custom MessageIdStrategy to control generation of WS-Addressing unique
   * message ids.
   */
  public fun messageIdStrategy(messageIdStrategy: String) {
    it.property("messageIdStrategy", messageIdStrategy)
  }

  /**
   * Spring org.springframework.ws.server.endpoint.MessageEndpoint for dispatching messages received
   * by Spring-WS to a Camel endpoint, to integrate with existing (legacy) endpoint mappings like
   * PayloadRootQNameEndpointMapping, SoapActionEndpointMapping, etc.
   */
  public fun endpointDispatcher(endpointDispatcher: String) {
    it.property("endpointDispatcher", endpointDispatcher)
  }

  /**
   * Reference to an instance of org.apache.camel.component.spring.ws.bean.CamelEndpointMapping in
   * the Registry/ApplicationContext. Only one bean is required in the registry to serve all
   * Camel/Spring-WS endpoints. This bean is auto-discovered by the MessageDispatcher and used to map
   * requests to Camel endpoints based on characteristics specified on the endpoint (like root QName,
   * SOAP action, etc)
   */
  public fun endpointMapping(endpointMapping: String) {
    it.property("endpointMapping", endpointMapping)
  }

  /**
   * The XPath expression to use when option type=xpathresult. Then this option is required to be
   * configured.
   */
  public fun expression(expression: String) {
    it.property("expression", expression)
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
   * Option to override soap response attachments in in/out exchange with attachments from the
   * actual service layer. If the invoked service appends or rewrites the soap attachments this option
   * when set to true, allows the modified soap attachments to be overwritten in in/out message
   * attachments
   */
  public fun allowResponseAttachmentOverride(allowResponseAttachmentOverride: String) {
    it.property("allowResponseAttachmentOverride", allowResponseAttachmentOverride)
  }

  /**
   * Option to override soap response attachments in in/out exchange with attachments from the
   * actual service layer. If the invoked service appends or rewrites the soap attachments this option
   * when set to true, allows the modified soap attachments to be overwritten in in/out message
   * attachments
   */
  public fun allowResponseAttachmentOverride(allowResponseAttachmentOverride: Boolean) {
    it.property("allowResponseAttachmentOverride", allowResponseAttachmentOverride.toString())
  }

  /**
   * Option to override soap response header in in/out exchange with header info from the actual
   * service layer. If the invoked service appends or rewrites the soap header this option when set to
   * true, allows the modified soap header to be overwritten in in/out message headers
   */
  public fun allowResponseHeaderOverride(allowResponseHeaderOverride: String) {
    it.property("allowResponseHeaderOverride", allowResponseHeaderOverride)
  }

  /**
   * Option to override soap response header in in/out exchange with header info from the actual
   * service layer. If the invoked service appends or rewrites the soap header this option when set to
   * true, allows the modified soap header to be overwritten in in/out message headers
   */
  public fun allowResponseHeaderOverride(allowResponseHeaderOverride: Boolean) {
    it.property("allowResponseHeaderOverride", allowResponseHeaderOverride.toString())
  }

  /**
   * Signifies the value for the faultAction response WS-Addressing Fault Action header that is
   * provided by the method. See org.springframework.ws.soap.addressing.server.annotation.Action
   * annotation for more details.
   */
  public fun faultAction(faultAction: String) {
    it.property("faultAction", faultAction)
  }

  /**
   * Signifies the value for the faultAction response WS-Addressing FaultTo header that is provided
   * by the method. See org.springframework.ws.soap.addressing.server.annotation.Action annotation for
   * more details.
   */
  public fun faultTo(faultTo: String) {
    it.property("faultTo", faultTo)
  }

  /**
   * Option to provide a custom WebServiceMessageFactory.
   */
  public fun messageFactory(messageFactory: String) {
    it.property("messageFactory", messageFactory)
  }

  /**
   * Option to provide a custom WebServiceMessageSender. For example to perform authentication or
   * use alternative transports
   */
  public fun messageSender(messageSender: String) {
    it.property("messageSender", messageSender)
  }

  /**
   * Signifies the value for the response WS-Addressing Action header that is provided by the
   * method. See org.springframework.ws.soap.addressing.server.annotation.Action annotation for more
   * details.
   */
  public fun outputAction(outputAction: String) {
    it.property("outputAction", outputAction)
  }

  /**
   * Signifies the value for the replyTo response WS-Addressing ReplyTo header that is provided by
   * the method. See org.springframework.ws.soap.addressing.server.annotation.Action annotation for
   * more details.
   */
  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  /**
   * SOAP action to include inside a SOAP request when accessing remote web services
   */
  public fun soapAction(soapAction: String) {
    it.property("soapAction", soapAction)
  }

  /**
   * Sets the socket read timeout (in milliseconds) while invoking a webservice using the producer,
   * see URLConnection.setReadTimeout() and CommonsHttpMessageSender.setReadTimeout(). This option
   * works when using the built-in message sender implementations: CommonsHttpMessageSender and
   * HttpUrlConnectionMessageSender. One of these implementations will be used by default for HTTP
   * based services unless you customize the Spring WS configuration options supplied to the component.
   * If you are using a non-standard sender, it is assumed that you will handle your own timeout
   * configuration. The built-in message sender HttpComponentsMessageSender is considered instead of
   * CommonsHttpMessageSender which has been deprecated, see
   * HttpComponentsMessageSender.setReadTimeout().
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets the socket read timeout (in milliseconds) while invoking a webservice using the producer,
   * see URLConnection.setReadTimeout() and CommonsHttpMessageSender.setReadTimeout(). This option
   * works when using the built-in message sender implementations: CommonsHttpMessageSender and
   * HttpUrlConnectionMessageSender. One of these implementations will be used by default for HTTP
   * based services unless you customize the Spring WS configuration options supplied to the component.
   * If you are using a non-standard sender, it is assumed that you will handle your own timeout
   * configuration. The built-in message sender HttpComponentsMessageSender is considered instead of
   * CommonsHttpMessageSender which has been deprecated, see
   * HttpComponentsMessageSender.setReadTimeout().
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Option to provide a custom WebServiceTemplate. This allows for full control over client-side
   * web services handling; like adding a custom interceptor or specifying a fault resolver, message
   * sender or message factory.
   */
  public fun webServiceTemplate(webServiceTemplate: String) {
    it.property("webServiceTemplate", webServiceTemplate)
  }

  /**
   * WS-Addressing 1.0 action header to include when accessing web services. The To header is set to
   * the address of the web service as specified in the endpoint URI (default Spring-WS behavior).
   */
  public fun wsAddressingAction(wsAddressingAction: String) {
    it.property("wsAddressingAction", wsAddressingAction)
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
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
