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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Send and receive messages to/from PubNub data stream network for connected devices.
 */
public fun UriDsl.pubnub(i: PubnubUriDsl.() -> Unit) {
  PubnubUriDsl(this).apply(i)
}

@CamelDslMarker
public class PubnubUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("pubnub")
  }

  private var channel: String = ""

  /**
   * The channel used for subscribing/publishing events
   */
  public fun channel(channel: String) {
    this.channel = channel
    it.url("$channel")
  }

  /**
   * UUID to be used as a device identifier, a default UUID is generated if not passed.
   */
  public fun uuid(uuid: String) {
    it.property("uuid", uuid)
  }

  /**
   * Also subscribe to related presence information
   */
  public fun withPresence(withPresence: String) {
    it.property("withPresence", withPresence)
  }

  /**
   * Also subscribe to related presence information
   */
  public fun withPresence(withPresence: Boolean) {
    it.property("withPresence", withPresence.toString())
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
   * The operation to perform. PUBLISH: Default. Send a message to all subscribers of a channel.
   * FIRE: allows the client to send a message to BLOCKS Event Handlers. These messages will go
   * directly to any Event Handlers registered on the channel. HERENOW: Obtain information about the
   * current state of a channel including a list of unique user-ids currently subscribed to the channel
   * and the total occupancy count. GETSTATE: Used to get key/value pairs specific to a subscriber
   * uuid. State information is supplied as a JSON object of key/value pairs SETSTATE: Used to set
   * key/value pairs specific to a subscriber uuid GETHISTORY: Fetches historical messages of a
   * channel.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
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
   * Reference to a Pubnub client in the registry.
   */
  public fun pubnub(pubnub: String) {
    it.property("pubnub", pubnub)
  }

  /**
   * If Access Manager is utilized, client will use this authKey in all restricted requests.
   */
  public fun authKey(authKey: String) {
    it.property("authKey", authKey)
  }

  /**
   * If cipher is passed, all communications to/from PubNub will be encrypted.
   */
  public fun cipherKey(cipherKey: String) {
    it.property("cipherKey", cipherKey)
  }

  /**
   * The publish key obtained from your PubNub account. Required when publishing messages.
   */
  public fun publishKey(publishKey: String) {
    it.property("publishKey", publishKey)
  }

  /**
   * The secret key used for message signing.
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Use SSL for secure transmission.
   */
  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  /**
   * Use SSL for secure transmission.
   */
  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  /**
   * The subscribe key obtained from your PubNub account. Required when subscribing to channels or
   * listening for presence events
   */
  public fun subscribeKey(subscribeKey: String) {
    it.property("subscribeKey", subscribeKey)
  }
}
