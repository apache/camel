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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Exchange messages with reactive stream processing libraries compatible with the reactive streams
 * standard.
 */
public fun UriDsl.`reactive-streams`(i: ReactiveStreamsUriDsl.() -> Unit) {
  ReactiveStreamsUriDsl(this).apply(i)
}

@CamelDslMarker
public class ReactiveStreamsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("reactive-streams")
  }

  private var stream: String = ""

  /**
   * Name of the stream channel used by the endpoint to exchange messages.
   */
  public fun stream(stream: String) {
    this.stream = stream
    it.url("$stream")
  }

  /**
   * Number of threads used to process exchanges in the Camel route.
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * Number of threads used to process exchanges in the Camel route.
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Set the low watermark of requested exchanges to the active subscription as percentage of the
   * maxInflightExchanges. When the number of pending items from the upstream source is lower than the
   * watermark, new items can be requested to the subscription. If set to 0, the subscriber will
   * request items in batches of maxInflightExchanges, only after all items of the previous batch have
   * been processed. If set to 1, the subscriber can request a new item each time an exchange is
   * processed (chatty). Any intermediate value can be used.
   */
  public fun exchangesRefillLowWatermark(exchangesRefillLowWatermark: String) {
    it.property("exchangesRefillLowWatermark", exchangesRefillLowWatermark)
  }

  /**
   * Set the low watermark of requested exchanges to the active subscription as percentage of the
   * maxInflightExchanges. When the number of pending items from the upstream source is lower than the
   * watermark, new items can be requested to the subscription. If set to 0, the subscriber will
   * request items in batches of maxInflightExchanges, only after all items of the previous batch have
   * been processed. If set to 1, the subscriber can request a new item each time an exchange is
   * processed (chatty). Any intermediate value can be used.
   */
  public fun exchangesRefillLowWatermark(exchangesRefillLowWatermark: Double) {
    it.property("exchangesRefillLowWatermark", exchangesRefillLowWatermark.toString())
  }

  /**
   * Determines if onComplete events should be pushed to the Camel route.
   */
  public fun forwardOnComplete(forwardOnComplete: String) {
    it.property("forwardOnComplete", forwardOnComplete)
  }

  /**
   * Determines if onComplete events should be pushed to the Camel route.
   */
  public fun forwardOnComplete(forwardOnComplete: Boolean) {
    it.property("forwardOnComplete", forwardOnComplete.toString())
  }

  /**
   * Determines if onError events should be pushed to the Camel route. Exceptions will be set as
   * message body.
   */
  public fun forwardOnError(forwardOnError: String) {
    it.property("forwardOnError", forwardOnError)
  }

  /**
   * Determines if onError events should be pushed to the Camel route. Exceptions will be set as
   * message body.
   */
  public fun forwardOnError(forwardOnError: Boolean) {
    it.property("forwardOnError", forwardOnError.toString())
  }

  /**
   * Maximum number of exchanges concurrently being processed by Camel. This parameter controls
   * backpressure on the stream. Setting a non-positive value will disable backpressure.
   */
  public fun maxInflightExchanges(maxInflightExchanges: String) {
    it.property("maxInflightExchanges", maxInflightExchanges)
  }

  /**
   * Maximum number of exchanges concurrently being processed by Camel. This parameter controls
   * backpressure on the stream. Setting a non-positive value will disable backpressure.
   */
  public fun maxInflightExchanges(maxInflightExchanges: Int) {
    it.property("maxInflightExchanges", maxInflightExchanges.toString())
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
   * The backpressure strategy to use when pushing events to a slow subscriber.
   */
  public fun backpressureStrategy(backpressureStrategy: String) {
    it.property("backpressureStrategy", backpressureStrategy)
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
