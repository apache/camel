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
 * Asynchronously call another endpoint from any Camel Context in the same JVM.
 */
public fun UriDsl.seda(i: SedaUriDsl.() -> Unit) {
  SedaUriDsl(this).apply(i)
}

@CamelDslMarker
public class SedaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("seda")
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
   * The maximum capacity of the SEDA queue (i.e., the number of messages it can hold). Will by
   * default use the defaultSize set on the SEDA component.
   */
  public fun size(size: String) {
    it.property("size", size)
  }

  /**
   * The maximum capacity of the SEDA queue (i.e., the number of messages it can hold). Will by
   * default use the defaultSize set on the SEDA component.
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
   * Whether to limit the number of concurrentConsumers to the maximum of 500. By default, an
   * exception will be thrown if an endpoint is configured with a greater number. You can disable that
   * check by turning this option off.
   */
  public fun limitConcurrentConsumers(limitConcurrentConsumers: String) {
    it.property("limitConcurrentConsumers", limitConcurrentConsumers)
  }

  /**
   * Whether to limit the number of concurrentConsumers to the maximum of 500. By default, an
   * exception will be thrown if an endpoint is configured with a greater number. You can disable that
   * check by turning this option off.
   */
  public fun limitConcurrentConsumers(limitConcurrentConsumers: Boolean) {
    it.property("limitConcurrentConsumers", limitConcurrentConsumers.toString())
  }

  /**
   * Specifies whether multiple consumers are allowed. If enabled, you can use SEDA for
   * Publish-Subscribe messaging. That is, you can send a message to the SEDA queue and have each
   * consumer receive a copy of the message. When enabled, this option should be specified on every
   * consumer endpoint.
   */
  public fun multipleConsumers(multipleConsumers: String) {
    it.property("multipleConsumers", multipleConsumers)
  }

  /**
   * Specifies whether multiple consumers are allowed. If enabled, you can use SEDA for
   * Publish-Subscribe messaging. That is, you can send a message to the SEDA queue and have each
   * consumer receive a copy of the message. When enabled, this option should be specified on every
   * consumer endpoint.
   */
  public fun multipleConsumers(multipleConsumers: Boolean) {
    it.property("multipleConsumers", multipleConsumers.toString())
  }

  /**
   * The timeout (in milliseconds) used when polling. When a timeout occurs, the consumer can check
   * whether it is allowed to continue running. Setting a lower value allows the consumer to react more
   * quickly upon shutdown.
   */
  public fun pollTimeout(pollTimeout: String) {
    it.property("pollTimeout", pollTimeout)
  }

  /**
   * The timeout (in milliseconds) used when polling. When a timeout occurs, the consumer can check
   * whether it is allowed to continue running. Setting a lower value allows the consumer to react more
   * quickly upon shutdown.
   */
  public fun pollTimeout(pollTimeout: Int) {
    it.property("pollTimeout", pollTimeout.toString())
  }

  /**
   * Whether to purge the task queue when stopping the consumer/route. This allows to stop faster,
   * as any pending messages on the queue is discarded.
   */
  public fun purgeWhenStopping(purgeWhenStopping: String) {
    it.property("purgeWhenStopping", purgeWhenStopping)
  }

  /**
   * Whether to purge the task queue when stopping the consumer/route. This allows to stop faster,
   * as any pending messages on the queue is discarded.
   */
  public fun purgeWhenStopping(purgeWhenStopping: Boolean) {
    it.property("purgeWhenStopping", purgeWhenStopping.toString())
  }

  /**
   * Whether a thread that sends messages to a full SEDA queue will block until the queue's capacity
   * is no longer exhausted. By default, an exception will be thrown stating that the queue is full. By
   * enabling this option, the calling thread will instead block and wait until the message can be
   * accepted.
   */
  public fun blockWhenFull(blockWhenFull: String) {
    it.property("blockWhenFull", blockWhenFull)
  }

  /**
   * Whether a thread that sends messages to a full SEDA queue will block until the queue's capacity
   * is no longer exhausted. By default, an exception will be thrown stating that the queue is full. By
   * enabling this option, the calling thread will instead block and wait until the message can be
   * accepted.
   */
  public fun blockWhenFull(blockWhenFull: Boolean) {
    it.property("blockWhenFull", blockWhenFull.toString())
  }

  /**
   * Whether the producer should discard the message (do not add the message to the queue), when
   * sending to a queue with no active consumers. Only one of the options discardIfNoConsumers and
   * failIfNoConsumers can be enabled at the same time.
   */
  public fun discardIfNoConsumers(discardIfNoConsumers: String) {
    it.property("discardIfNoConsumers", discardIfNoConsumers)
  }

  /**
   * Whether the producer should discard the message (do not add the message to the queue), when
   * sending to a queue with no active consumers. Only one of the options discardIfNoConsumers and
   * failIfNoConsumers can be enabled at the same time.
   */
  public fun discardIfNoConsumers(discardIfNoConsumers: Boolean) {
    it.property("discardIfNoConsumers", discardIfNoConsumers.toString())
  }

  /**
   * Whether a thread that sends messages to a full SEDA queue will be discarded. By default, an
   * exception will be thrown stating that the queue is full. By enabling this option, the calling
   * thread will give up sending and continue, meaning that the message was not sent to the SEDA queue.
   */
  public fun discardWhenFull(discardWhenFull: String) {
    it.property("discardWhenFull", discardWhenFull)
  }

  /**
   * Whether a thread that sends messages to a full SEDA queue will be discarded. By default, an
   * exception will be thrown stating that the queue is full. By enabling this option, the calling
   * thread will give up sending and continue, meaning that the message was not sent to the SEDA queue.
   */
  public fun discardWhenFull(discardWhenFull: Boolean) {
    it.property("discardWhenFull", discardWhenFull.toString())
  }

  /**
   * Whether the producer should fail by throwing an exception, when sending to a queue with no
   * active consumers. Only one of the options discardIfNoConsumers and failIfNoConsumers can be
   * enabled at the same time.
   */
  public fun failIfNoConsumers(failIfNoConsumers: String) {
    it.property("failIfNoConsumers", failIfNoConsumers)
  }

  /**
   * Whether the producer should fail by throwing an exception, when sending to a queue with no
   * active consumers. Only one of the options discardIfNoConsumers and failIfNoConsumers can be
   * enabled at the same time.
   */
  public fun failIfNoConsumers(failIfNoConsumers: Boolean) {
    it.property("failIfNoConsumers", failIfNoConsumers.toString())
  }

  /**
   * Offer timeout (in milliseconds) can be added to the block case when queue is full. You can
   * disable timeout by using 0 or a negative value.
   */
  public fun offerTimeout(offerTimeout: String) {
    it.property("offerTimeout", offerTimeout)
  }

  /**
   * Timeout (in milliseconds) before a SEDA producer will stop waiting for an asynchronous task to
   * complete. You can disable timeout by using 0 or a negative value.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Option to specify whether the caller should wait for the async task to complete or not before
   * continuing. The following three options are supported: Always, Never or IfReplyExpected. The first
   * two values are self-explanatory. The last value, IfReplyExpected, will only wait if the message is
   * Request Reply based. The default option is IfReplyExpected.
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

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: String) {
    it.property("browseLimit", browseLimit)
  }

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: Int) {
    it.property("browseLimit", browseLimit.toString())
  }

  /**
   * Define the queue instance which will be used by the endpoint
   */
  public fun queue(queue: String) {
    it.property("queue", queue)
  }
}
