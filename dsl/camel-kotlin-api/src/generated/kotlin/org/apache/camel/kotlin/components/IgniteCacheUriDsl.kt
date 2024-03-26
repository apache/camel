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
 * Perform cache operations on an Ignite cache or consume changes from a continuous query.
 */
public fun UriDsl.`ignite-cache`(i: IgniteCacheUriDsl.() -> Unit) {
  IgniteCacheUriDsl(this).apply(i)
}

@CamelDslMarker
public class IgniteCacheUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ignite-cache")
  }

  private var cacheName: String = ""

  /**
   * The cache name.
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * Sets whether to propagate the incoming body if the return type of the underlying Ignite
   * operation is void.
   */
  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: String) {
    it.property("propagateIncomingBodyIfNoReturnValue", propagateIncomingBodyIfNoReturnValue)
  }

  /**
   * Sets whether to propagate the incoming body if the return type of the underlying Ignite
   * operation is void.
   */
  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: Boolean) {
    it.property("propagateIncomingBodyIfNoReturnValue",
        propagateIncomingBodyIfNoReturnValue.toString())
  }

  /**
   * Sets whether to treat Collections as cache objects or as Collections of items to
   * insert/update/compute, etc.
   */
  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: String) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects)
  }

  /**
   * Sets whether to treat Collections as cache objects or as Collections of items to
   * insert/update/compute, etc.
   */
  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: Boolean) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects.toString())
  }

  /**
   * Whether auto unsubscribe is enabled in the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_AUTO_UNSUBSCRIBE
   */
  public fun autoUnsubscribe(autoUnsubscribe: String) {
    it.property("autoUnsubscribe", autoUnsubscribe)
  }

  /**
   * Whether auto unsubscribe is enabled in the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_AUTO_UNSUBSCRIBE
   */
  public fun autoUnsubscribe(autoUnsubscribe: Boolean) {
    it.property("autoUnsubscribe", autoUnsubscribe.toString())
  }

  /**
   * Whether to process existing results that match the query. Used on initialization of the
   * Continuous Query Consumer.
   */
  public fun fireExistingQueryResults(fireExistingQueryResults: String) {
    it.property("fireExistingQueryResults", fireExistingQueryResults)
  }

  /**
   * Whether to process existing results that match the query. Used on initialization of the
   * Continuous Query Consumer.
   */
  public fun fireExistingQueryResults(fireExistingQueryResults: Boolean) {
    it.property("fireExistingQueryResults", fireExistingQueryResults.toString())
  }

  /**
   * Whether to pack each update in an individual Exchange, even if multiple updates are received in
   * one batch. Only used by the Continuous Query Consumer.
   */
  public fun oneExchangePerUpdate(oneExchangePerUpdate: String) {
    it.property("oneExchangePerUpdate", oneExchangePerUpdate)
  }

  /**
   * Whether to pack each update in an individual Exchange, even if multiple updates are received in
   * one batch. Only used by the Continuous Query Consumer.
   */
  public fun oneExchangePerUpdate(oneExchangePerUpdate: Boolean) {
    it.property("oneExchangePerUpdate", oneExchangePerUpdate.toString())
  }

  /**
   * The page size. Only used by the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_PAGE_SIZE
   */
  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  /**
   * The page size. Only used by the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_PAGE_SIZE
   */
  public fun pageSize(pageSize: Int) {
    it.property("pageSize", pageSize.toString())
  }

  /**
   * The Query to execute, only needed for operations that require it, and for the Continuous Query
   * Consumer.
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * The remote filter, only used by the Continuous Query Consumer.
   */
  public fun remoteFilter(remoteFilter: String) {
    it.property("remoteFilter", remoteFilter)
  }

  /**
   * The time interval for the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_TIME_INTERVAL
   */
  public fun timeInterval(timeInterval: String) {
    it.property("timeInterval", timeInterval)
  }

  /**
   * The time interval for the Continuous Query Consumer. Default value notice:
   * ContinuousQuery.DFLT_TIME_INTERVAL
   */
  public fun timeInterval(timeInterval: Int) {
    it.property("timeInterval", timeInterval.toString())
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
   * The CachePeekMode, only needed for operations that require it (IgniteCacheOperation#SIZE).
   */
  public fun cachePeekMode(cachePeekMode: String) {
    it.property("cachePeekMode", cachePeekMode)
  }

  /**
   * Whether to fail the initialization if the cache doesn't exist.
   */
  public fun failIfInexistentCache(failIfInexistentCache: String) {
    it.property("failIfInexistentCache", failIfInexistentCache)
  }

  /**
   * Whether to fail the initialization if the cache doesn't exist.
   */
  public fun failIfInexistentCache(failIfInexistentCache: Boolean) {
    it.property("failIfInexistentCache", failIfInexistentCache.toString())
  }

  /**
   * The cache operation to invoke. Possible values: GET, PUT, REMOVE, SIZE, REBALANCE, QUERY,
   * CLEAR.
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
}
