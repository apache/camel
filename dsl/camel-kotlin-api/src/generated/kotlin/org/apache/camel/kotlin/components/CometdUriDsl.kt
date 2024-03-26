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
 * Offers publish/subscribe, peer-to-peer (via a server), and RPC style messaging using the
 * CometD/Bayeux protocol.
 */
public fun UriDsl.cometd(i: CometdUriDsl.() -> Unit) {
  CometdUriDsl(this).apply(i)
}

@CamelDslMarker
public class CometdUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cometd")
  }

  private var host: String = ""

  private var port: String = ""

  private var channelName: String = ""

  /**
   * Hostname
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$channelName")
  }

  /**
   * Host port number
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$channelName")
  }

  /**
   * Host port number
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$channelName")
  }

  /**
   * The channelName represents a topic that can be subscribed to by the Camel endpoints.
   */
  public fun channelName(channelName: String) {
    this.channelName = channelName
    it.url("$host:$port/$channelName")
  }

  /**
   * The origins domain that support to cross, if the crosssOriginFilterOn is true
   */
  public fun allowedOrigins(allowedOrigins: String) {
    it.property("allowedOrigins", allowedOrigins)
  }

  /**
   * The root directory for the web resources or classpath. Use the protocol file: or classpath:
   * depending if you want that the component loads the resource from file system or classpath.
   * Classpath is required for OSGI deployment where the resources are packaged in the jar
   */
  public fun baseResource(baseResource: String) {
    it.property("baseResource", baseResource)
  }

  /**
   * If true, the server will support for cross-domain filtering
   */
  public fun crossOriginFilterOn(crossOriginFilterOn: String) {
    it.property("crossOriginFilterOn", crossOriginFilterOn)
  }

  /**
   * If true, the server will support for cross-domain filtering
   */
  public fun crossOriginFilterOn(crossOriginFilterOn: Boolean) {
    it.property("crossOriginFilterOn", crossOriginFilterOn.toString())
  }

  /**
   * The filterPath will be used by the CrossOriginFilter, if the crosssOriginFilterOn is true
   */
  public fun filterPath(filterPath: String) {
    it.property("filterPath", filterPath)
  }

  /**
   * The client side poll timeout in milliseconds. How long a client will wait between reconnects
   */
  public fun interval(interval: String) {
    it.property("interval", interval)
  }

  /**
   * The client side poll timeout in milliseconds. How long a client will wait between reconnects
   */
  public fun interval(interval: Int) {
    it.property("interval", interval.toString())
  }

  /**
   * If true, the server will accept JSON wrapped in a comment and will generate JSON wrapped in a
   * comment. This is a defence against Ajax Hijacking.
   */
  public fun jsonCommented(jsonCommented: String) {
    it.property("jsonCommented", jsonCommented)
  }

  /**
   * If true, the server will accept JSON wrapped in a comment and will generate JSON wrapped in a
   * comment. This is a defence against Ajax Hijacking.
   */
  public fun jsonCommented(jsonCommented: Boolean) {
    it.property("jsonCommented", jsonCommented.toString())
  }

  /**
   * Logging level. 0=none, 1=info, 2=debug.
   */
  public fun logLevel(logLevel: String) {
    it.property("logLevel", logLevel)
  }

  /**
   * Logging level. 0=none, 1=info, 2=debug.
   */
  public fun logLevel(logLevel: Int) {
    it.property("logLevel", logLevel.toString())
  }

  /**
   * The max client side poll timeout in milliseconds. A client will be removed if a connection is
   * not received in this time.
   */
  public fun maxInterval(maxInterval: String) {
    it.property("maxInterval", maxInterval)
  }

  /**
   * The max client side poll timeout in milliseconds. A client will be removed if a connection is
   * not received in this time.
   */
  public fun maxInterval(maxInterval: Int) {
    it.property("maxInterval", maxInterval.toString())
  }

  /**
   * The client side poll timeout, if multiple connections are detected from the same browser.
   */
  public fun multiFrameInterval(multiFrameInterval: String) {
    it.property("multiFrameInterval", multiFrameInterval)
  }

  /**
   * The client side poll timeout, if multiple connections are detected from the same browser.
   */
  public fun multiFrameInterval(multiFrameInterval: Int) {
    it.property("multiFrameInterval", multiFrameInterval.toString())
  }

  /**
   * The server side poll timeout in milliseconds. This is how long the server will hold a reconnect
   * request before responding.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * The server side poll timeout in milliseconds. This is how long the server will hold a reconnect
   * request before responding.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Whether to include the server session headers in the Camel message when creating a Camel
   * Message for incoming requests.
   */
  public fun sessionHeadersEnabled(sessionHeadersEnabled: String) {
    it.property("sessionHeadersEnabled", sessionHeadersEnabled)
  }

  /**
   * Whether to include the server session headers in the Camel message when creating a Camel
   * Message for incoming requests.
   */
  public fun sessionHeadersEnabled(sessionHeadersEnabled: Boolean) {
    it.property("sessionHeadersEnabled", sessionHeadersEnabled.toString())
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
   * Whether to disconnect local sessions after publishing a message to its channel. Disconnecting
   * local session is needed as they are not swept by default by CometD, and therefore you can run out
   * of memory.
   */
  public fun disconnectLocalSession(disconnectLocalSession: String) {
    it.property("disconnectLocalSession", disconnectLocalSession)
  }

  /**
   * Whether to disconnect local sessions after publishing a message to its channel. Disconnecting
   * local session is needed as they are not swept by default by CometD, and therefore you can run out
   * of memory.
   */
  public fun disconnectLocalSession(disconnectLocalSession: Boolean) {
    it.property("disconnectLocalSession", disconnectLocalSession.toString())
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
}
