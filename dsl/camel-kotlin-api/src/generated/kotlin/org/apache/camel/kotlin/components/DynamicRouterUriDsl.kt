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
 * The Dynamic Router component routes exchanges to recipients, and the recipients (and their rules)
 * may change at runtime.
 */
public fun UriDsl.`dynamic-router`(i: DynamicRouterUriDsl.() -> Unit) {
  DynamicRouterUriDsl(this).apply(i)
}

@CamelDslMarker
public class DynamicRouterUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dynamic-router")
  }

  private var channel: String = ""

  /**
   * Channel for the Dynamic Router. For example, if the Dynamic Router URI is
   * dynamic-router://test, then the channel is test. Channels are a way of keeping routing
   * participants, their rules, and exchanges logically separate from the participants, rules, and
   * exchanges on other channels. This can be seen as analogous to VLANs in networking.
   */
  public fun channel(channel: String) {
    this.channel = channel
    it.url("$channel")
  }

  /**
   * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a
   * single outgoing message from the Multicast. By default, Camel will use the last reply as the
   * outgoing message. You can also use a POJO as the AggregationStrategy.
   */
  public fun aggregationStrategy(aggregationStrategy: String) {
    it.property("aggregationStrategy", aggregationStrategy)
  }

  /**
   * Refers to an AggregationStrategy to be used to assemble the replies from the multicasts, into a
   * single outgoing message from the Multicast. By default, Camel will use the last reply as the
   * outgoing message. You can also use a POJO as the AggregationStrategy.
   */
  public fun aggregationStrategyBean(aggregationStrategyBean: String) {
    it.property("aggregationStrategyBean", aggregationStrategyBean)
  }

  /**
   * If this option is false then the aggregate method is not used if there was no data to enrich.
   * If this option is true then null values is used as the oldExchange (when no data to enrich), when
   * using POJOs as the AggregationStrategy
   */
  public fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: String) {
    it.property("aggregationStrategyMethodAllowNull", aggregationStrategyMethodAllowNull)
  }

  /**
   * If this option is false then the aggregate method is not used if there was no data to enrich.
   * If this option is true then null values is used as the oldExchange (when no data to enrich), when
   * using POJOs as the AggregationStrategy
   */
  public fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: Boolean) {
    it.property("aggregationStrategyMethodAllowNull", aggregationStrategyMethodAllowNull.toString())
  }

  /**
   * You can use a POJO as the AggregationStrategy. This refers to the name of the method that
   * aggregates the exchanges.
   */
  public fun aggregationStrategyMethodName(aggregationStrategyMethodName: String) {
    it.property("aggregationStrategyMethodName", aggregationStrategyMethodName)
  }

  /**
   * When caching producer endpoints, this is the size of the cache. Default is 100.
   */
  public fun cacheSize(cacheSize: String) {
    it.property("cacheSize", cacheSize)
  }

  /**
   * When caching producer endpoints, this is the size of the cache. Default is 100.
   */
  public fun cacheSize(cacheSize: Int) {
    it.property("cacheSize", cacheSize.toString())
  }

  /**
   * Refers to a custom Thread Pool to be used for parallel processing. Notice that, if you set this
   * option, then parallel processing is automatically implied, and you do not have to enable that
   * option in addition to this one.
   */
  public fun executorService(executorService: String) {
    it.property("executorService", executorService)
  }

  /**
   * Refers to a custom Thread Pool to be used for parallel processing. Notice that, if you set this
   * option, then parallel processing is automatically implied, and you do not have to enable that
   * option in addition to this one.
   */
  public fun executorServiceBean(executorServiceBean: String) {
    it.property("executorServiceBean", executorServiceBean)
  }

  /**
   * Ignore the invalid endpoint exception when attempting to create a producer with an invalid
   * endpoint.
   */
  public fun ignoreInvalidEndpoints(ignoreInvalidEndpoints: String) {
    it.property("ignoreInvalidEndpoints", ignoreInvalidEndpoints)
  }

  /**
   * Ignore the invalid endpoint exception when attempting to create a producer with an invalid
   * endpoint.
   */
  public fun ignoreInvalidEndpoints(ignoreInvalidEndpoints: Boolean) {
    it.property("ignoreInvalidEndpoints", ignoreInvalidEndpoints.toString())
  }

  /**
   * Uses the Processor when preparing the org.apache.camel.Exchange to be sent. This can be used to
   * deep-clone messages that should be sent, or to provide any custom logic that is needed before the
   * exchange is sent. This is the name of a bean in the registry.
   */
  public fun onPrepare(onPrepare: String) {
    it.property("onPrepare", onPrepare)
  }

  /**
   * Uses the Processor when preparing the org.apache.camel.Exchange to be sent. This can be used to
   * deep-clone messages that should be sent, or to provide any custom logic that is needed before the
   * exchange is sent. This is a Processor instance.
   */
  public fun onPrepareProcessor(onPrepareProcessor: String) {
    it.property("onPrepareProcessor", onPrepareProcessor)
  }

  /**
   * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice
   * that this would require the implementation of AggregationStrategy to be implemented as
   * thread-safe. By default, this is false, meaning that Camel synchronizes the call to the aggregate
   * method. Though, in some use-cases, this can be used to archive higher performance when the
   * AggregationStrategy is implemented as thread-safe.
   */
  public fun parallelAggregate(parallelAggregate: String) {
    it.property("parallelAggregate", parallelAggregate)
  }

  /**
   * If enabled then the aggregate method on AggregationStrategy can be called concurrently. Notice
   * that this would require the implementation of AggregationStrategy to be implemented as
   * thread-safe. By default, this is false, meaning that Camel synchronizes the call to the aggregate
   * method. Though, in some use-cases, this can be used to archive higher performance when the
   * AggregationStrategy is implemented as thread-safe.
   */
  public fun parallelAggregate(parallelAggregate: Boolean) {
    it.property("parallelAggregate", parallelAggregate.toString())
  }

  /**
   * If enabled, then sending via multicast occurs concurrently. Note that the caller thread will
   * still wait until all messages have been fully processed before it continues. It is only the
   * sending and processing of the replies from the multicast recipients that happens concurrently.
   * When parallel processing is enabled, then the Camel routing engine will continue processing using
   * the last used thread from the parallel thread pool. However, if you want to use the original
   * thread that called the multicast, then make sure to enable the synchronous option as well.
   */
  public fun parallelProcessing(parallelProcessing: String) {
    it.property("parallelProcessing", parallelProcessing)
  }

  /**
   * If enabled, then sending via multicast occurs concurrently. Note that the caller thread will
   * still wait until all messages have been fully processed before it continues. It is only the
   * sending and processing of the replies from the multicast recipients that happens concurrently.
   * When parallel processing is enabled, then the Camel routing engine will continue processing using
   * the last used thread from the parallel thread pool. However, if you want to use the original
   * thread that called the multicast, then make sure to enable the synchronous option as well.
   */
  public fun parallelProcessing(parallelProcessing: Boolean) {
    it.property("parallelProcessing", parallelProcessing.toString())
  }

  /**
   * Recipient mode: firstMatch or allMatch
   */
  public fun recipientMode(recipientMode: String) {
    it.property("recipientMode", recipientMode)
  }

  /**
   * Shares the org.apache.camel.spi.UnitOfWork with the parent and each of the sub messages.
   * Multicast will, by default, not share a unit of work between the parent exchange and each
   * multicasted exchange. This means each sub exchange has its own individual unit of work.
   */
  public fun shareUnitOfWork(shareUnitOfWork: String) {
    it.property("shareUnitOfWork", shareUnitOfWork)
  }

  /**
   * Shares the org.apache.camel.spi.UnitOfWork with the parent and each of the sub messages.
   * Multicast will, by default, not share a unit of work between the parent exchange and each
   * multicasted exchange. This means each sub exchange has its own individual unit of work.
   */
  public fun shareUnitOfWork(shareUnitOfWork: Boolean) {
    it.property("shareUnitOfWork", shareUnitOfWork.toString())
  }

  /**
   * Will stop further processing if an exception or failure occurred during processing of an
   * org.apache.camel.Exchange and the caused exception will be thrown. Will also stop if processing
   * the exchange failed (has a fault message), or an exception was thrown and handled by the error
   * handler (such as using onException). In all situations, the multicast will stop further
   * processing. This is the same behavior as in the pipeline that is used by the routing engine. The
   * default behavior is to not stop, but to continue processing until the end.
   */
  public fun stopOnException(stopOnException: String) {
    it.property("stopOnException", stopOnException)
  }

  /**
   * Will stop further processing if an exception or failure occurred during processing of an
   * org.apache.camel.Exchange and the caused exception will be thrown. Will also stop if processing
   * the exchange failed (has a fault message), or an exception was thrown and handled by the error
   * handler (such as using onException). In all situations, the multicast will stop further
   * processing. This is the same behavior as in the pipeline that is used by the routing engine. The
   * default behavior is to not stop, but to continue processing until the end.
   */
  public fun stopOnException(stopOnException: Boolean) {
    it.property("stopOnException", stopOnException.toString())
  }

  /**
   * If enabled, then Camel will process replies out-of-order (e.g., in the order they come back).
   * If disabled, Camel will process replies in the same order as defined by the multicast.
   */
  public fun streaming(streaming: String) {
    it.property("streaming", streaming)
  }

  /**
   * If enabled, then Camel will process replies out-of-order (e.g., in the order they come back).
   * If disabled, Camel will process replies in the same order as defined by the multicast.
   */
  public fun streaming(streaming: Boolean) {
    it.property("streaming", streaming.toString())
  }

  /**
   * Sets whether synchronous processing should be strictly used. When enabled then the same thread
   * is used to continue routing after the multicast is complete, even if parallel processing is
   * enabled.
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used. When enabled then the same thread
   * is used to continue routing after the multicast is complete, even if parallel processing is
   * enabled.
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * Sets a total timeout specified in milliseconds, when using parallel processing. If the
   * Multicast has not been able to send and process all replies within the given timeframe, then the
   * timeout triggers and the Multicast breaks out and continues. Notice that, if you provide a
   * TimeoutAwareAggregationStrategy, then the timeout method is invoked before breaking out. If the
   * timeout is reached with running tasks still remaining, certain tasks (for which it is difficult
   * for Camel to shut down in a graceful manner) may continue to run. So use this option with a bit of
   * care.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets a total timeout specified in milliseconds, when using parallel processing. If the
   * Multicast has not been able to send and process all replies within the given timeframe, then the
   * timeout triggers and the Multicast breaks out and continues. Notice that, if you provide a
   * TimeoutAwareAggregationStrategy, then the timeout method is invoked before breaking out. If the
   * timeout is reached with running tasks still remaining, certain tasks (for which it is difficult
   * for Camel to shut down in a graceful manner) may continue to run. So use this option with a bit of
   * care.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Flag to log a warning if no predicates match for an exchange.
   */
  public fun warnDroppedMessage(warnDroppedMessage: String) {
    it.property("warnDroppedMessage", warnDroppedMessage)
  }

  /**
   * Flag to log a warning if no predicates match for an exchange.
   */
  public fun warnDroppedMessage(warnDroppedMessage: Boolean) {
    it.property("warnDroppedMessage", warnDroppedMessage.toString())
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
