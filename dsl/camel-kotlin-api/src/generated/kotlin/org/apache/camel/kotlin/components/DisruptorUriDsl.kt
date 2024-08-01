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
 * Provides asynchronous SEDA behavior using LMAX Disruptor.
 */
public fun UriDsl.disruptor(i: DisruptorUriDsl.() -> Unit) {
  DisruptorUriDsl(this).apply(i)
}

@CamelDslMarker
public class DisruptorUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("disruptor")
  }

  private var name: String = ""

  /**
   * Name of queue
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * The maximum capacity of the Disruptors ringbuffer Will be effectively increased to the nearest
   * power of two. Notice: Mind if you use this option, then it's the first endpoint being created with
   * the queue name that determines the size. To make sure all endpoints use the same size, then
   * configure the size option on all of them, or the first endpoint being created.
   */
  public fun size(size: String) {
    it.property("size", size)
  }

  /**
   * The maximum capacity of the Disruptors ringbuffer Will be effectively increased to the nearest
   * power of two. Notice: Mind if you use this option, then it's the first endpoint being created with
   * the queue name that determines the size. To make sure all endpoints use the same size, then
   * configure the size option on all of them, or the first endpoint being created.
   */
  public fun size(size: Int) {
    it.property("size", size.toString())
  }

  /**
   * Number of concurrent threads processing exchanges.
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * Number of concurrent threads processing exchanges.
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Specifies whether multiple consumers are allowed. If enabled, you can use Disruptor for
   * Publish-Subscribe messaging. That is, you can send a message to the queue and have each consumer
   * receive a copy of the message. When enabled, this option should be specified on every consumer
   * endpoint.
   */
  public fun multipleConsumers(multipleConsumers: String) {
    it.property("multipleConsumers", multipleConsumers)
  }

  /**
   * Specifies whether multiple consumers are allowed. If enabled, you can use Disruptor for
   * Publish-Subscribe messaging. That is, you can send a message to the queue and have each consumer
   * receive a copy of the message. When enabled, this option should be specified on every consumer
   * endpoint.
   */
  public fun multipleConsumers(multipleConsumers: Boolean) {
    it.property("multipleConsumers", multipleConsumers.toString())
  }

  /**
   * Defines the strategy used by consumer threads to wait on new exchanges to be published. The
   * options allowed are:Blocking, Sleeping, BusySpin and Yielding.
   */
  public fun waitStrategy(waitStrategy: String) {
    it.property("waitStrategy", waitStrategy)
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
   * Whether a thread that sends messages to a full Disruptor will block until the ringbuffer's
   * capacity is no longer exhausted. By default, the calling thread will block and wait until the
   * message can be accepted. By disabling this option, an exception will be thrown stating that the
   * queue is full.
   */
  public fun blockWhenFull(blockWhenFull: String) {
    it.property("blockWhenFull", blockWhenFull)
  }

  /**
   * Whether a thread that sends messages to a full Disruptor will block until the ringbuffer's
   * capacity is no longer exhausted. By default, the calling thread will block and wait until the
   * message can be accepted. By disabling this option, an exception will be thrown stating that the
   * queue is full.
   */
  public fun blockWhenFull(blockWhenFull: Boolean) {
    it.property("blockWhenFull", blockWhenFull.toString())
  }

  /**
   * Defines the producers allowed on the Disruptor. The options allowed are: Multi to allow
   * multiple producers and Single to enable certain optimizations only allowed when one concurrent
   * producer (on one thread or otherwise synchronized) is active.
   */
  public fun producerType(producerType: String) {
    it.property("producerType", producerType)
  }

  /**
   * Timeout (in milliseconds) before a producer will stop waiting for an asynchronous task to
   * complete. You can disable timeout by using 0 or a negative value.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Option to specify whether the caller should wait for the async task to complete or not before
   * continuing. The following three options are supported: Always, Never or IfReplyExpected. The first
   * two values are self-explanatory. The last value, IfReplyExpected, will only wait if the message is
   * Request Reply based.
   */
  public fun waitForTaskToComplete(waitForTaskToComplete: String) {
    it.property("waitForTaskToComplete", waitForTaskToComplete)
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
