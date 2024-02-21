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

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$path")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$path")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$path")
  }

  public fun path(path: String) {
    this.path = path
    it.url("$host:$port/$path")
  }

  public fun allowedOriginPattern(allowedOriginPattern: String) {
    it.property("allowedOriginPattern", allowedOriginPattern)
  }

  public fun allowOriginHeader(allowOriginHeader: String) {
    it.property("allowOriginHeader", allowOriginHeader)
  }

  public fun allowOriginHeader(allowOriginHeader: Boolean) {
    it.property("allowOriginHeader", allowOriginHeader.toString())
  }

  public fun consumeAsClient(consumeAsClient: String) {
    it.property("consumeAsClient", consumeAsClient)
  }

  public fun consumeAsClient(consumeAsClient: Boolean) {
    it.property("consumeAsClient", consumeAsClient.toString())
  }

  public fun fireWebSocketConnectionEvents(fireWebSocketConnectionEvents: String) {
    it.property("fireWebSocketConnectionEvents", fireWebSocketConnectionEvents)
  }

  public fun fireWebSocketConnectionEvents(fireWebSocketConnectionEvents: Boolean) {
    it.property("fireWebSocketConnectionEvents", fireWebSocketConnectionEvents.toString())
  }

  public fun maxReconnectAttempts(maxReconnectAttempts: String) {
    it.property("maxReconnectAttempts", maxReconnectAttempts)
  }

  public fun maxReconnectAttempts(maxReconnectAttempts: Int) {
    it.property("maxReconnectAttempts", maxReconnectAttempts.toString())
  }

  public fun originHeaderUrl(originHeaderUrl: String) {
    it.property("originHeaderUrl", originHeaderUrl)
  }

  public fun reconnectInitialDelay(reconnectInitialDelay: String) {
    it.property("reconnectInitialDelay", reconnectInitialDelay)
  }

  public fun reconnectInitialDelay(reconnectInitialDelay: Int) {
    it.property("reconnectInitialDelay", reconnectInitialDelay.toString())
  }

  public fun reconnectInterval(reconnectInterval: String) {
    it.property("reconnectInterval", reconnectInterval)
  }

  public fun reconnectInterval(reconnectInterval: Int) {
    it.property("reconnectInterval", reconnectInterval.toString())
  }

  public fun router(router: String) {
    it.property("router", router)
  }

  public fun serverOptions(serverOptions: String) {
    it.property("serverOptions", serverOptions)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun clientOptions(clientOptions: String) {
    it.property("clientOptions", clientOptions)
  }

  public fun clientSubProtocols(clientSubProtocols: String) {
    it.property("clientSubProtocols", clientSubProtocols)
  }

  public fun sendToAll(sendToAll: String) {
    it.property("sendToAll", sendToAll)
  }

  public fun sendToAll(sendToAll: Boolean) {
    it.property("sendToAll", sendToAll.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
