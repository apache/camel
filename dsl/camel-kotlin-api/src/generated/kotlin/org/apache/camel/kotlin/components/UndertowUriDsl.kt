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
 * Expose HTTP and WebSocket endpoints and access external HTTP/WebSocket servers.
 */
public fun UriDsl.undertow(i: UndertowUriDsl.() -> Unit) {
  UndertowUriDsl(this).apply(i)
}

@CamelDslMarker
public class UndertowUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("undertow")
  }

  private var httpURI: String = ""

  /**
   * The url of the HTTP endpoint to use.
   */
  public fun httpURI(httpURI: String) {
    this.httpURI = httpURI
    it.url("$httpURI")
  }

  /**
   * For HTTP endpoint: if true, text and binary messages will be wrapped as java.io.InputStream
   * before they are passed to an Exchange; otherwise they will be passed as byte. For WebSocket
   * endpoint: if true, text and binary messages will be wrapped as java.io.Reader and
   * java.io.InputStream respectively before they are passed to an Exchange; otherwise they will be
   * passed as String and byte respectively.
   */
  public fun useStreaming(useStreaming: String) {
    it.property("useStreaming", useStreaming)
  }

  /**
   * For HTTP endpoint: if true, text and binary messages will be wrapped as java.io.InputStream
   * before they are passed to an Exchange; otherwise they will be passed as byte. For WebSocket
   * endpoint: if true, text and binary messages will be wrapped as java.io.Reader and
   * java.io.InputStream respectively before they are passed to an Exchange; otherwise they will be
   * passed as String and byte respectively.
   */
  public fun useStreaming(useStreaming: Boolean) {
    it.property("useStreaming", useStreaming.toString())
  }

  /**
   * Whether or not the consumer should write access log
   */
  public fun accessLog(accessLog: String) {
    it.property("accessLog", accessLog)
  }

  /**
   * Whether or not the consumer should write access log
   */
  public fun accessLog(accessLog: Boolean) {
    it.property("accessLog", accessLog.toString())
  }

  /**
   * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple
   * methods can be specified separated by comma.
   */
  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: String) {
    it.property("matchOnUriPrefix", matchOnUriPrefix)
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: Boolean) {
    it.property("matchOnUriPrefix", matchOnUriPrefix.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: String) {
    it.property("muteException", muteException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: Boolean) {
    it.property("muteException", muteException.toString())
  }

  /**
   * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is
   * turned off.
   */
  public fun optionsEnabled(optionsEnabled: String) {
    it.property("optionsEnabled", optionsEnabled)
  }

  /**
   * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is
   * turned off.
   */
  public fun optionsEnabled(optionsEnabled: Boolean) {
    it.property("optionsEnabled", optionsEnabled.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side and if the caused Exception
   * was send back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is instead of the
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Java will deserialize the incoming data
   * from the request to Java and that can be a potential security risk.
   */
  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side and if the caused Exception
   * was send back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is instead of the
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Java will deserialize the incoming data
   * from the request to Java and that can be a potential security risk.
   */
  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
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
   * Specifies a comma-delimited set of io.undertow.server.HttpHandler instances to lookup in your
   * Registry. These handlers are added to the Undertow handler chain (for example, to add security).
   * Important: You can not use different handlers with different Undertow endpoints using the same
   * port number. The handlers is associated to the port number. If you need different handlers, then
   * use different port numbers.
   */
  public fun handlers(handlers: String) {
    it.property("handlers", handlers)
  }

  /**
   * Configure a cookie handler to maintain a HTTP session
   */
  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  /**
   * Setting to ensure socket is not closed due to inactivity
   */
  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  /**
   * Setting to ensure socket is not closed due to inactivity
   */
  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  /**
   * Sets additional channel options. The options that can be used are defined in org.xnio.Options.
   * To configure from endpoint uri, then prefix each option with option., such as
   * option.close-abort=true&option.send-buffer=8192
   */
  public fun options(options: String) {
    it.property("options", options)
  }

  /**
   * If the option is true, UndertowProducer will set the Host header to the value contained in the
   * current exchange Host header, useful in reverse proxy applications where you want the Host header
   * received by the downstream server to reflect the URL called by the upstream client, this allows
   * applications which use the Host header to generate accurate URL's for a proxied service.
   */
  public fun preserveHostHeader(preserveHostHeader: String) {
    it.property("preserveHostHeader", preserveHostHeader)
  }

  /**
   * If the option is true, UndertowProducer will set the Host header to the value contained in the
   * current exchange Host header, useful in reverse proxy applications where you want the Host header
   * received by the downstream server to reflect the URL called by the upstream client, this allows
   * applications which use the Host header to generate accurate URL's for a proxied service.
   */
  public fun preserveHostHeader(preserveHostHeader: Boolean) {
    it.property("preserveHostHeader", preserveHostHeader.toString())
  }

  /**
   * Setting to facilitate socket multiplexing
   */
  public fun reuseAddresses(reuseAddresses: String) {
    it.property("reuseAddresses", reuseAddresses)
  }

  /**
   * Setting to facilitate socket multiplexing
   */
  public fun reuseAddresses(reuseAddresses: Boolean) {
    it.property("reuseAddresses", reuseAddresses.toString())
  }

  /**
   * Setting to improve TCP protocol performance
   */
  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  /**
   * Setting to improve TCP protocol performance
   */
  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
  }

  /**
   * Option to disable throwing the HttpOperationFailedException in case of failed responses from
   * the remote server. This allows you to get all responses regardless of the HTTP status code.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  /**
   * Option to disable throwing the HttpOperationFailedException in case of failed responses from
   * the remote server. This allows you to get all responses regardless of the HTTP status code.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
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
   * Which Undertow AccessLogReceiver should be used Will use JBossLoggingAccessLogReceiver if not
   * specified
   */
  public fun accessLogReceiver(accessLogReceiver: String) {
    it.property("accessLogReceiver", accessLogReceiver)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * To use a custom UndertowHttpBinding to control the mapping between Camel message and undertow.
   */
  public fun undertowHttpBinding(undertowHttpBinding: String) {
    it.property("undertowHttpBinding", undertowHttpBinding)
  }

  /**
   * Configuration used by UndertowSecurityProvider. Comma separated list of allowed roles.
   */
  public fun allowedRoles(allowedRoles: String) {
    it.property("allowedRoles", allowedRoles)
  }

  /**
   * OConfiguration used by UndertowSecurityProvider. Security configuration object for use from
   * UndertowSecurityProvider. Configuration is UndertowSecurityProvider specific. Each provider
   * decides whether accepts configuration.
   */
  public fun securityConfiguration(securityConfiguration: String) {
    it.property("securityConfiguration", securityConfiguration)
  }

  /**
   * Security provider allows plug in the provider, which will be used to secure requests. SPI
   * approach could be used too (endpoint then finds security provider using SPI).
   */
  public fun securityProvider(securityProvider: String) {
    it.property("securityProvider", securityProvider)
  }

  /**
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * if true, the consumer will post notifications to the route when a new WebSocket peer connects,
   * disconnects, etc. See UndertowConstants.EVENT_TYPE and EventType.
   */
  public fun fireWebSocketChannelEvents(fireWebSocketChannelEvents: String) {
    it.property("fireWebSocketChannelEvents", fireWebSocketChannelEvents)
  }

  /**
   * if true, the consumer will post notifications to the route when a new WebSocket peer connects,
   * disconnects, etc. See UndertowConstants.EVENT_TYPE and EventType.
   */
  public fun fireWebSocketChannelEvents(fireWebSocketChannelEvents: Boolean) {
    it.property("fireWebSocketChannelEvents", fireWebSocketChannelEvents.toString())
  }

  /**
   * Timeout in milliseconds when sending to a websocket channel. The default timeout is 30000 (30
   * seconds).
   */
  public fun sendTimeout(sendTimeout: String) {
    it.property("sendTimeout", sendTimeout)
  }

  /**
   * Timeout in milliseconds when sending to a websocket channel. The default timeout is 30000 (30
   * seconds).
   */
  public fun sendTimeout(sendTimeout: Int) {
    it.property("sendTimeout", sendTimeout.toString())
  }

  /**
   * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of
   * having to use the UndertowConstants.SEND_TO_ALL header on the message.
   */
  public fun sendToAll(sendToAll: String) {
    it.property("sendToAll", sendToAll)
  }

  /**
   * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of
   * having to use the UndertowConstants.SEND_TO_ALL header on the message.
   */
  public fun sendToAll(sendToAll: Boolean) {
    it.property("sendToAll", sendToAll.toString())
  }
}
