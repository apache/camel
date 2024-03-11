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
 * Read and write from/to Infinispan distributed key/value store and data grid.
 */
public fun UriDsl.`infinispan-embedded`(i: InfinispanEmbeddedUriDsl.() -> Unit) {
  InfinispanEmbeddedUriDsl(this).apply(i)
}

@CamelDslMarker
public class InfinispanEmbeddedUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("infinispan-embedded")
  }

  private var cacheName: String = ""

  /**
   * The name of the cache to use. Use current to use the existing cache name from the currently
   * configured cached manager. Or use default for the default cache manager name.
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * Specifies the query builder.
   */
  public fun queryBuilder(queryBuilder: String) {
    it.property("queryBuilder", queryBuilder)
  }

  /**
   * If true, the listener will be installed for the entire cluster
   */
  public fun clusteredListener(clusteredListener: String) {
    it.property("clusteredListener", clusteredListener)
  }

  /**
   * If true, the listener will be installed for the entire cluster
   */
  public fun clusteredListener(clusteredListener: Boolean) {
    it.property("clusteredListener", clusteredListener.toString())
  }

  /**
   * Returns the custom listener in use, if provided
   */
  public fun customListener(customListener: String) {
    it.property("customListener", customListener)
  }

  /**
   * Specifies the set of event types to register by the consumer.Multiple event can be separated by
   * comma. The possible event types are: CACHE_ENTRY_ACTIVATED, CACHE_ENTRY_PASSIVATED,
   * CACHE_ENTRY_VISITED, CACHE_ENTRY_LOADED, CACHE_ENTRY_EVICTED, CACHE_ENTRY_CREATED,
   * CACHE_ENTRY_REMOVED, CACHE_ENTRY_MODIFIED, TRANSACTION_COMPLETED, TRANSACTION_REGISTERED,
   * CACHE_ENTRY_INVALIDATED, CACHE_ENTRY_EXPIRED, DATA_REHASHED, TOPOLOGY_CHANGED,
   * PARTITION_STATUS_CHANGED, PERSISTENCE_AVAILABILITY_CHANGED
   */
  public fun eventTypes(eventTypes: String) {
    it.property("eventTypes", eventTypes)
  }

  /**
   * If true, the consumer will receive notifications synchronously
   */
  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  /**
   * If true, the consumer will receive notifications synchronously
   */
  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
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
   * Set a specific default value for some producer operations
   */
  public fun defaultValue(defaultValue: String) {
    it.property("defaultValue", defaultValue)
  }

  /**
   * Set a specific key for producer operations
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * Set a specific old value for some producer operations
   */
  public fun oldValue(oldValue: String) {
    it.property("oldValue", oldValue)
  }

  /**
   * The operation to perform
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Set a specific value for producer operations
   */
  public fun `value`(`value`: String) {
    it.property("value", value)
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
   * Specifies the cache Container to connect
   */
  public fun cacheContainer(cacheContainer: String) {
    it.property("cacheContainer", cacheContainer)
  }

  /**
   * The CacheContainer configuration. Used if the cacheContainer is not defined.
   */
  public fun cacheContainerConfiguration(cacheContainerConfiguration: String) {
    it.property("cacheContainerConfiguration", cacheContainerConfiguration)
  }

  /**
   * An implementation specific URI for the CacheManager
   */
  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  /**
   * A comma separated list of org.infinispan.context.Flag to be applied by default on each cache
   * invocation
   */
  public fun flags(flags: String) {
    it.property("flags", flags)
  }

  /**
   * Set a specific remappingFunction to use in a compute operation.
   */
  public fun remappingFunction(remappingFunction: String) {
    it.property("remappingFunction", remappingFunction)
  }

  /**
   * Store the operation result in a header instead of the message body. By default, resultHeader ==
   * null and the query result is stored in the message body, any existing content in the message body
   * is discarded. If resultHeader is set, the value is used as the name of the header to store the
   * query result and the original message body is preserved. This value can be overridden by an in
   * message header named: CamelInfinispanOperationResultHeader
   */
  public fun resultHeader(resultHeader: String) {
    it.property("resultHeader", resultHeader)
  }
}
