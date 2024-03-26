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
 * Perform caching operations against JSR107/JCache.
 */
public fun UriDsl.jcache(i: JcacheUriDsl.() -> Unit) {
  JcacheUriDsl(this).apply(i)
}

@CamelDslMarker
public class JcacheUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jcache")
  }

  private var cacheName: String = ""

  /**
   * The name of the cache
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * The Properties for the javax.cache.spi.CachingProvider to create the CacheManager
   */
  public fun cacheConfigurationProperties(cacheConfigurationProperties: String) {
    it.property("cacheConfigurationProperties", cacheConfigurationProperties)
  }

  /**
   * The fully qualified class name of the javax.cache.spi.CachingProvider
   */
  public fun cachingProvider(cachingProvider: String) {
    it.property("cachingProvider", cachingProvider)
  }

  /**
   * An implementation specific URI for the CacheManager
   */
  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  /**
   * Whether management gathering is enabled
   */
  public fun managementEnabled(managementEnabled: String) {
    it.property("managementEnabled", managementEnabled)
  }

  /**
   * Whether management gathering is enabled
   */
  public fun managementEnabled(managementEnabled: Boolean) {
    it.property("managementEnabled", managementEnabled.toString())
  }

  /**
   * If read-through caching should be used
   */
  public fun readThrough(readThrough: String) {
    it.property("readThrough", readThrough)
  }

  /**
   * If read-through caching should be used
   */
  public fun readThrough(readThrough: Boolean) {
    it.property("readThrough", readThrough.toString())
  }

  /**
   * Whether statistics gathering is enabled
   */
  public fun statisticsEnabled(statisticsEnabled: String) {
    it.property("statisticsEnabled", statisticsEnabled)
  }

  /**
   * Whether statistics gathering is enabled
   */
  public fun statisticsEnabled(statisticsEnabled: Boolean) {
    it.property("statisticsEnabled", statisticsEnabled.toString())
  }

  /**
   * If cache should use store-by-value or store-by-reference semantics
   */
  public fun storeByValue(storeByValue: String) {
    it.property("storeByValue", storeByValue)
  }

  /**
   * If cache should use store-by-value or store-by-reference semantics
   */
  public fun storeByValue(storeByValue: Boolean) {
    it.property("storeByValue", storeByValue.toString())
  }

  /**
   * If write-through caching should be used
   */
  public fun writeThrough(writeThrough: String) {
    it.property("writeThrough", writeThrough)
  }

  /**
   * If write-through caching should be used
   */
  public fun writeThrough(writeThrough: Boolean) {
    it.property("writeThrough", writeThrough.toString())
  }

  /**
   * Events a consumer should filter (multiple events can be separated by comma). If using
   * filteredEvents option, then eventFilters one will be ignored
   */
  public fun filteredEvents(filteredEvents: String) {
    it.property("filteredEvents", filteredEvents)
  }

  /**
   * if the old value is required for events
   */
  public fun oldValueRequired(oldValueRequired: String) {
    it.property("oldValueRequired", oldValueRequired)
  }

  /**
   * if the old value is required for events
   */
  public fun oldValueRequired(oldValueRequired: Boolean) {
    it.property("oldValueRequired", oldValueRequired.toString())
  }

  /**
   * if the event listener should block the thread causing the event
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * if the event listener should block the thread causing the event
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
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
   * The CacheEntryEventFilter. If using eventFilters option, then filteredEvents one will be
   * ignored
   */
  public fun eventFilters(eventFilters: String) {
    it.property("eventFilters", eventFilters)
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
   * To configure using a cache operation by default. If an operation in the message header, then
   * the operation from the header takes precedence.
   */
  public fun action(action: String) {
    it.property("action", action)
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
   * A Configuration for the Cache
   */
  public fun cacheConfiguration(cacheConfiguration: String) {
    it.property("cacheConfiguration", cacheConfiguration)
  }

  /**
   * The CacheLoader factory
   */
  public fun cacheLoaderFactory(cacheLoaderFactory: String) {
    it.property("cacheLoaderFactory", cacheLoaderFactory)
  }

  /**
   * The CacheWriter factory
   */
  public fun cacheWriterFactory(cacheWriterFactory: String) {
    it.property("cacheWriterFactory", cacheWriterFactory)
  }

  /**
   * Configure if a cache need to be created if it does exist or can't be pre-configured.
   */
  public fun createCacheIfNotExists(createCacheIfNotExists: String) {
    it.property("createCacheIfNotExists", createCacheIfNotExists)
  }

  /**
   * Configure if a cache need to be created if it does exist or can't be pre-configured.
   */
  public fun createCacheIfNotExists(createCacheIfNotExists: Boolean) {
    it.property("createCacheIfNotExists", createCacheIfNotExists.toString())
  }

  /**
   * The ExpiryPolicy factory
   */
  public fun expiryPolicyFactory(expiryPolicyFactory: String) {
    it.property("expiryPolicyFactory", expiryPolicyFactory)
  }

  /**
   * Configure if a camel-cache should try to find implementations of jcache api in runtimes like
   * OSGi.
   */
  public fun lookupProviders(lookupProviders: String) {
    it.property("lookupProviders", lookupProviders)
  }

  /**
   * Configure if a camel-cache should try to find implementations of jcache api in runtimes like
   * OSGi.
   */
  public fun lookupProviders(lookupProviders: Boolean) {
    it.property("lookupProviders", lookupProviders.toString())
  }
}
