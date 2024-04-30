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
 * Expose WebSocket endpoints and connect to remote WebSocket servers using Vert.x
 */
public fun UriDsl.`vertx-websocket`(i: VertxWebsocketUriDsl.() -> Unit) {
  VertxWebsocketUriDsl(this).apply(i)
}

@CamelDslMarker
public class VertxWebsocketUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("vertx-websocket")
  }

  private var host: String = ""

  private var port: String = ""

  private var path: String = ""

  /**
   * WebSocket hostname, such as localhost or a remote host when in client mode.
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$path")
  }

  /**
   * WebSocket port number to use.
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$path")
  }

  /**
   * WebSocket port number to use.
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$path")
  }

  /**
   * WebSocket path to use.
   */
  public fun path(path: String) {
    this.path = path
    it.url("$host:$port/$path")
  }

  /**
   * Regex pattern to match the origin header sent by WebSocket clients
   */
  public fun allowedOriginPattern(allowedOriginPattern: String) {
    it.property("allowedOriginPattern", allowedOriginPattern)
  }

  /**
   * Whether the WebSocket client should add the Origin header to the WebSocket handshake request.
   */
  public fun allowOriginHeader(allowOriginHeader: String) {
    it.property("allowOriginHeader", allowOriginHeader)
  }

  /**
   * Whether the WebSocket client should add the Origin header to the WebSocket handshake request.
   */
  public fun allowOriginHeader(allowOriginHeader: Boolean) {
    it.property("allowOriginHeader", allowOriginHeader.toString())
  }

  /**
   * When set to true, the consumer acts as a WebSocket client, creating exchanges on each received
   * WebSocket event.
   */
  public fun consumeAsClient(consumeAsClient: String) {
    it.property("consumeAsClient", consumeAsClient)
  }

  /**
   * When set to true, the consumer acts as a WebSocket client, creating exchanges on each received
   * WebSocket event.
   */
  public fun consumeAsClient(consumeAsClient: Boolean) {
    it.property("consumeAsClient", consumeAsClient.toString())
  }

  /**
   * Whether the server consumer will create a message exchange when a new WebSocket peer connects
   * or disconnects
   */
  public fun fireWebSocketConnectionEvents(fireWebSocketConnectionEvents: String) {
    it.property("fireWebSocketConnectionEvents", fireWebSocketConnectionEvents)
  }

  /**
   * Whether the server consumer will create a message exchange when a new WebSocket peer connects
   * or disconnects
   */
  public fun fireWebSocketConnectionEvents(fireWebSocketConnectionEvents: Boolean) {
    it.property("fireWebSocketConnectionEvents", fireWebSocketConnectionEvents.toString())
  }

  /**
   * Headers to send in the HTTP handshake request. When the endpoint is a consumer, it only works
   * when it consumes a remote host as a client (i.e. consumeAsClient is true).
   */
  public fun handshakeHeaders(handshakeHeaders: String) {
    it.property("handshakeHeaders", handshakeHeaders)
  }

  /**
   * When consumeAsClient is set to true this sets the maximum number of allowed reconnection
   * attempts to a previously closed WebSocket. A value of 0 (the default) will attempt to reconnect
   * indefinitely.
   */
  public fun maxReconnectAttempts(maxReconnectAttempts: String) {
    it.property("maxReconnectAttempts", maxReconnectAttempts)
  }

  /**
   * When consumeAsClient is set to true this sets the maximum number of allowed reconnection
   * attempts to a previously closed WebSocket. A value of 0 (the default) will attempt to reconnect
   * indefinitely.
   */
  public fun maxReconnectAttempts(maxReconnectAttempts: Int) {
    it.property("maxReconnectAttempts", maxReconnectAttempts.toString())
  }

  /**
   * The value of the Origin header that the WebSocket client should use on the WebSocket handshake
   * request. When not specified, the WebSocket client will automatically determine the value for the
   * Origin from the request URL.
   */
  public fun originHeaderUrl(originHeaderUrl: String) {
    it.property("originHeaderUrl", originHeaderUrl)
  }

  /**
   * When consumeAsClient is set to true this sets the initial delay in milliseconds before
   * attempting to reconnect to a previously closed WebSocket.
   */
  public fun reconnectInitialDelay(reconnectInitialDelay: String) {
    it.property("reconnectInitialDelay", reconnectInitialDelay)
  }

  /**
   * When consumeAsClient is set to true this sets the initial delay in milliseconds before
   * attempting to reconnect to a previously closed WebSocket.
   */
  public fun reconnectInitialDelay(reconnectInitialDelay: Int) {
    it.property("reconnectInitialDelay", reconnectInitialDelay.toString())
  }

  /**
   * When consumeAsClient is set to true this sets the interval in milliseconds at which
   * reconnecting to a previously closed WebSocket occurs.
   */
  public fun reconnectInterval(reconnectInterval: String) {
    it.property("reconnectInterval", reconnectInterval)
  }

  /**
   * When consumeAsClient is set to true this sets the interval in milliseconds at which
   * reconnecting to a previously closed WebSocket occurs.
   */
  public fun reconnectInterval(reconnectInterval: Int) {
    it.property("reconnectInterval", reconnectInterval.toString())
  }

  /**
   * To use an existing vertx router for the HTTP server
   */
  public fun router(router: String) {
    it.property("router", router)
  }

  /**
   * Sets customized options for configuring the HTTP server hosting the WebSocket for the consumer
   */
  public fun serverOptions(serverOptions: String) {
    it.property("serverOptions", serverOptions)
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
   * Sets customized options for configuring the WebSocket client used in the producer
   */
  public fun clientOptions(clientOptions: String) {
    it.property("clientOptions", clientOptions)
  }

  /**
   * Comma separated list of WebSocket subprotocols that the client should use for the
   * Sec-WebSocket-Protocol header
   */
  public fun clientSubProtocols(clientSubProtocols: String) {
    it.property("clientSubProtocols", clientSubProtocols)
  }

  /**
   * To send to all websocket subscribers. Can be used to configure at the endpoint level, instead
   * of providing the VertxWebsocketConstants.SEND_TO_ALL header on the message. Note that when using
   * this option, the host name specified for the vertx-websocket producer URI must match one used for
   * an existing vertx-websocket consumer. Note that this option only applies when producing messages
   * to endpoints hosted by the vertx-websocket consumer and not to an externally hosted WebSocket.
   */
  public fun sendToAll(sendToAll: String) {
    it.property("sendToAll", sendToAll)
  }

  /**
   * To send to all websocket subscribers. Can be used to configure at the endpoint level, instead
   * of providing the VertxWebsocketConstants.SEND_TO_ALL header on the message. Note that when using
   * this option, the host name specified for the vertx-websocket producer URI must match one used for
   * an existing vertx-websocket consumer. Note that this option only applies when producing messages
   * to endpoints hosted by the vertx-websocket consumer and not to an externally hosted WebSocket.
   */
  public fun sendToAll(sendToAll: Boolean) {
    it.property("sendToAll", sendToAll.toString())
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
