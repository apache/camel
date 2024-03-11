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
 * Perform caching operations using Ehcache.
 */
public fun UriDsl.ehcache(i: EhcacheUriDsl.() -> Unit) {
  EhcacheUriDsl(this).apply(i)
}

@CamelDslMarker
public class EhcacheUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ehcache")
  }

  private var cacheName: String = ""

  /**
   * the cache name
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * The cache manager
   */
  public fun cacheManager(cacheManager: String) {
    it.property("cacheManager", cacheManager)
  }

  /**
   * The cache manager configuration
   */
  public fun cacheManagerConfiguration(cacheManagerConfiguration: String) {
    it.property("cacheManagerConfiguration", cacheManagerConfiguration)
  }

  /**
   * URI pointing to the Ehcache XML configuration file's location
   */
  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  /**
   * Configure if a cache need to be created if it does exist or can't be pre-configured.
   */
  public fun createCacheIfNotExist(createCacheIfNotExist: String) {
    it.property("createCacheIfNotExist", createCacheIfNotExist)
  }

  /**
   * Configure if a cache need to be created if it does exist or can't be pre-configured.
   */
  public fun createCacheIfNotExist(createCacheIfNotExist: Boolean) {
    it.property("createCacheIfNotExist", createCacheIfNotExist.toString())
  }

  /**
   * Set the delivery mode (synchronous, asynchronous)
   */
  public fun eventFiring(eventFiring: String) {
    it.property("eventFiring", eventFiring)
  }

  /**
   * Set the delivery mode (ordered, unordered)
   */
  public fun eventOrdering(eventOrdering: String) {
    it.property("eventOrdering", eventOrdering)
  }

  /**
   * Set the type of events to listen for (EVICTED,EXPIRED,REMOVED,CREATED,UPDATED). You can specify
   * multiple entries separated by comma.
   */
  public fun eventTypes(eventTypes: String) {
    it.property("eventTypes", eventTypes)
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
   * To configure the default cache action. If an action is set in the message header, then the
   * operation from the header takes precedence.
   */
  public fun action(action: String) {
    it.property("action", action)
  }

  /**
   * To configure the default action key. If a key is set in the message header, then the key from
   * the header takes precedence.
   */
  public fun key(key: String) {
    it.property("key", key)
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
   * The default cache configuration to be used to create caches.
   */
  public fun configuration(configuration: String) {
    it.property("configuration", configuration)
  }

  /**
   * A map of cache configuration to be used to create caches.
   */
  public fun configurations(configurations: String) {
    it.property("configurations", configurations)
  }

  /**
   * The cache key type, default java.lang.Object
   */
  public fun keyType(keyType: String) {
    it.property("keyType", keyType)
  }

  /**
   * The cache value type, default java.lang.Object
   */
  public fun valueType(valueType: String) {
    it.property("valueType", valueType)
  }
}
