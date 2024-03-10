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
 * Send and receive events from Knative.
 */
public fun UriDsl.knative(i: KnativeUriDsl.() -> Unit) {
  KnativeUriDsl(this).apply(i)
}

@CamelDslMarker
public class KnativeUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("knative")
  }

  private var type: String = ""

  private var typeId: String = ""

  /**
   * The Knative resource type
   */
  public fun type(type: String) {
    this.type = type
    it.url("$type/$typeId")
  }

  /**
   * The identifier of the Knative resource
   */
  public fun typeId(typeId: String) {
    this.typeId = typeId
    it.url("$type/$typeId")
  }

  /**
   * CloudEvent headers to override
   */
  public fun ceOverride(ceOverride: String) {
    it.property("ceOverride", ceOverride)
  }

  /**
   * Set the version of the cloudevents spec.
   */
  public fun cloudEventsSpecVersion(cloudEventsSpecVersion: String) {
    it.property("cloudEventsSpecVersion", cloudEventsSpecVersion)
  }

  /**
   * Set the event-type information of the produced events.
   */
  public fun cloudEventsType(cloudEventsType: String) {
    it.property("cloudEventsType", cloudEventsType)
  }

  /**
   * The environment
   */
  public fun environment(environment: String) {
    it.property("environment", environment)
  }

  /**
   * Set the filters.
   */
  public fun filters(filters: String) {
    it.property("filters", filters)
  }

  /**
   * The SinkBinding configuration.
   */
  public fun sinkBinding(sinkBinding: String) {
    it.property("sinkBinding", sinkBinding)
  }

  /**
   * Set the transport options.
   */
  public fun transportOptions(transportOptions: String) {
    it.property("transportOptions", transportOptions)
  }

  /**
   * Transforms the reply into a cloud event that will be processed by the caller. When listening to
   * events from a Knative Broker, if this flag is enabled, replies will be published to the same
   * Broker where the request comes from (beware that if you don't change the type of the received
   * message, you may create a loop and receive your same reply). When this flag is disabled,
   * CloudEvent headers are removed from the reply.
   */
  public fun replyWithCloudEvent(replyWithCloudEvent: String) {
    it.property("replyWithCloudEvent", replyWithCloudEvent)
  }

  /**
   * Transforms the reply into a cloud event that will be processed by the caller. When listening to
   * events from a Knative Broker, if this flag is enabled, replies will be published to the same
   * Broker where the request comes from (beware that if you don't change the type of the received
   * message, you may create a loop and receive your same reply). When this flag is disabled,
   * CloudEvent headers are removed from the reply.
   */
  public fun replyWithCloudEvent(replyWithCloudEvent: Boolean) {
    it.property("replyWithCloudEvent", replyWithCloudEvent.toString())
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
   * If the consumer should construct a full reply to knative request.
   */
  public fun reply(reply: String) {
    it.property("reply", reply)
  }

  /**
   * If the consumer should construct a full reply to knative request.
   */
  public fun reply(reply: Boolean) {
    it.property("reply", reply.toString())
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
   * The version of the k8s resource referenced by the endpoint.
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * The type of the k8s resource referenced by the endpoint.
   */
  public fun kind(kind: String) {
    it.property("kind", kind)
  }

  /**
   * The name of the k8s resource referenced by the endpoint.
   */
  public fun name(name: String) {
    it.property("name", name)
  }
}
