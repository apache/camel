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
 * Send and receive messages to/from Google Cloud Platform PubSub Service.
 */
public fun UriDsl.`google-pubsub`(i: GooglePubsubUriDsl.() -> Unit) {
  GooglePubsubUriDsl(this).apply(i)
}

@CamelDslMarker
public class GooglePubsubUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("google-pubsub")
  }

  private var projectId: String = ""

  private var destinationName: String = ""

  /**
   * The Google Cloud PubSub Project Id
   */
  public fun projectId(projectId: String) {
    this.projectId = projectId
    it.url("$projectId:$destinationName")
  }

  /**
   * The Destination Name. For the consumer this will be the subscription name, while for the
   * producer this will be the topic name.
   */
  public fun destinationName(destinationName: String) {
    this.destinationName = destinationName
    it.url("$projectId:$destinationName")
  }

  /**
   * Use Credentials when interacting with PubSub service (no authentication is required when using
   * emulator).
   */
  public fun authenticate(authenticate: String) {
    it.property("authenticate", authenticate)
  }

  /**
   * Use Credentials when interacting with PubSub service (no authentication is required when using
   * emulator).
   */
  public fun authenticate(authenticate: Boolean) {
    it.property("authenticate", authenticate.toString())
  }

  /**
   * Logger ID to use when a match to the parent route required
   */
  public fun loggerId(loggerId: String) {
    it.property("loggerId", loggerId)
  }

  /**
   * The Service account key that can be used as credentials for the PubSub publisher/subscriber. It
   * can be loaded by default from classpath, but you can prefix with classpath:, file:, or http: to
   * load the resource from different systems.
   */
  public fun serviceAccountKey(serviceAccountKey: String) {
    it.property("serviceAccountKey", serviceAccountKey)
  }

  /**
   * AUTO = exchange gets ack'ed/nack'ed on completion. NONE = downstream process has to ack/nack
   * explicitly
   */
  public fun ackMode(ackMode: String) {
    it.property("ackMode", ackMode)
  }

  /**
   * The number of parallel streams consuming from the subscription
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * The number of parallel streams consuming from the subscription
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Set the maximum period a message ack deadline will be extended. Value in seconds
   */
  public fun maxAckExtensionPeriod(maxAckExtensionPeriod: String) {
    it.property("maxAckExtensionPeriod", maxAckExtensionPeriod)
  }

  /**
   * Set the maximum period a message ack deadline will be extended. Value in seconds
   */
  public fun maxAckExtensionPeriod(maxAckExtensionPeriod: Int) {
    it.property("maxAckExtensionPeriod", maxAckExtensionPeriod.toString())
  }

  /**
   * The max number of messages to receive from the server in a single API call
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * The max number of messages to receive from the server in a single API call
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * Synchronously pull batches of messages
   */
  public fun synchronousPull(synchronousPull: String) {
    it.property("synchronousPull", synchronousPull)
  }

  /**
   * Synchronously pull batches of messages
   */
  public fun synchronousPull(synchronousPull: Boolean) {
    it.property("synchronousPull", synchronousPull.toString())
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
   * Should message ordering be enabled
   */
  public fun messageOrderingEnabled(messageOrderingEnabled: String) {
    it.property("messageOrderingEnabled", messageOrderingEnabled)
  }

  /**
   * Should message ordering be enabled
   */
  public fun messageOrderingEnabled(messageOrderingEnabled: Boolean) {
    it.property("messageOrderingEnabled", messageOrderingEnabled.toString())
  }

  /**
   * Pub/Sub endpoint to use. Required when using message ordering, and ensures that messages are
   * received in order even when multiple publishers are used
   */
  public fun pubsubEndpoint(pubsubEndpoint: String) {
    it.property("pubsubEndpoint", pubsubEndpoint)
  }

  /**
   * A custom GooglePubsubSerializer to use for serializing message payloads in the producer
   */
  public fun serializer(serializer: String) {
    it.property("serializer", serializer)
  }
}
