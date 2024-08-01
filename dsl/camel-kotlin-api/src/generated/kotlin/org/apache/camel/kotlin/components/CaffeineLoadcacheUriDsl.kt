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
 * Perform caching operations using Caffeine Cache with an attached CacheLoader.
 */
public fun UriDsl.`caffeine-loadcache`(i: CaffeineLoadcacheUriDsl.() -> Unit) {
  CaffeineLoadcacheUriDsl(this).apply(i)
}

@CamelDslMarker
public class CaffeineLoadcacheUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("caffeine-loadcache")
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
   * To configure the default cache action. If an action is set in the message header, then the
   * operation from the header takes precedence.
   */
  public fun action(action: String) {
    it.property("action", action)
  }

  /**
   * Automatic create the Caffeine cache if none has been configured or exists in the registry.
   */
  public fun createCacheIfNotExist(createCacheIfNotExist: String) {
    it.property("createCacheIfNotExist", createCacheIfNotExist)
  }

  /**
   * Automatic create the Caffeine cache if none has been configured or exists in the registry.
   */
  public fun createCacheIfNotExist(createCacheIfNotExist: Boolean) {
    it.property("createCacheIfNotExist", createCacheIfNotExist.toString())
  }

  /**
   * Set the eviction Type for this cache
   */
  public fun evictionType(evictionType: String) {
    it.property("evictionType", evictionType)
  }

  /**
   * Specifies that each entry should be automatically removed from the cache once a fixed duration
   * has elapsed after the entry's creation, the most recent replacement of its value, or its last
   * read. Access time is reset by all cache read and write operations. The unit is in seconds.
   */
  public fun expireAfterAccessTime(expireAfterAccessTime: String) {
    it.property("expireAfterAccessTime", expireAfterAccessTime)
  }

  /**
   * Specifies that each entry should be automatically removed from the cache once a fixed duration
   * has elapsed after the entry's creation, the most recent replacement of its value, or its last
   * read. Access time is reset by all cache read and write operations. The unit is in seconds.
   */
  public fun expireAfterAccessTime(expireAfterAccessTime: Int) {
    it.property("expireAfterAccessTime", expireAfterAccessTime.toString())
  }

  /**
   * Specifies that each entry should be automatically removed from the cache once a fixed duration
   * has elapsed after the entry's creation, or the most recent replacement of its value. The unit is
   * in seconds.
   */
  public fun expireAfterWriteTime(expireAfterWriteTime: String) {
    it.property("expireAfterWriteTime", expireAfterWriteTime)
  }

  /**
   * Specifies that each entry should be automatically removed from the cache once a fixed duration
   * has elapsed after the entry's creation, or the most recent replacement of its value. The unit is
   * in seconds.
   */
  public fun expireAfterWriteTime(expireAfterWriteTime: Int) {
    it.property("expireAfterWriteTime", expireAfterWriteTime.toString())
  }

  /**
   * Sets the minimum total size for the internal data structures. Providing a large enough estimate
   * at construction time avoids the need for expensive resizing operations later, but setting this
   * value unnecessarily high wastes memory.
   */
  public fun initialCapacity(initialCapacity: String) {
    it.property("initialCapacity", initialCapacity)
  }

  /**
   * Sets the minimum total size for the internal data structures. Providing a large enough estimate
   * at construction time avoids the need for expensive resizing operations later, but setting this
   * value unnecessarily high wastes memory.
   */
  public fun initialCapacity(initialCapacity: Int) {
    it.property("initialCapacity", initialCapacity.toString())
  }

  /**
   * To configure the default action key. If a key is set in the message header, then the key from
   * the header takes precedence.
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * Specifies the maximum number of entries the cache may contain. Note that the cache may evict an
   * entry before this limit is exceeded or temporarily exceed the threshold while evicting. As the
   * cache size grows close to the maximum, the cache evicts entries that are less likely to be used
   * again. For example, the cache may evict an entry because it hasn't been used recently or very
   * often. When size is zero, elements will be evicted immediately after being loaded into the cache.
   * This can be useful in testing or to disable caching temporarily without a code change. As eviction
   * is scheduled on the configured executor, tests may instead prefer to configure the cache to
   * execute tasks directly on the same thread.
   */
  public fun maximumSize(maximumSize: String) {
    it.property("maximumSize", maximumSize)
  }

  /**
   * Specifies the maximum number of entries the cache may contain. Note that the cache may evict an
   * entry before this limit is exceeded or temporarily exceed the threshold while evicting. As the
   * cache size grows close to the maximum, the cache evicts entries that are less likely to be used
   * again. For example, the cache may evict an entry because it hasn't been used recently or very
   * often. When size is zero, elements will be evicted immediately after being loaded into the cache.
   * This can be useful in testing or to disable caching temporarily without a code change. As eviction
   * is scheduled on the configured executor, tests may instead prefer to configure the cache to
   * execute tasks directly on the same thread.
   */
  public fun maximumSize(maximumSize: Int) {
    it.property("maximumSize", maximumSize.toString())
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
   * To configure a CacheLoader in case of a LoadCache use
   */
  public fun cacheLoader(cacheLoader: String) {
    it.property("cacheLoader", cacheLoader)
  }

  /**
   * Set a specific removal Listener for the cache
   */
  public fun removalListener(removalListener: String) {
    it.property("removalListener", removalListener)
  }

  /**
   * Set a specific Stats Counter for the cache stats
   */
  public fun statsCounter(statsCounter: String) {
    it.property("statsCounter", statsCounter)
  }

  /**
   * To enable stats on the cache
   */
  public fun statsEnabled(statsEnabled: String) {
    it.property("statsEnabled", statsEnabled)
  }

  /**
   * To enable stats on the cache
   */
  public fun statsEnabled(statsEnabled: Boolean) {
    it.property("statsEnabled", statsEnabled.toString())
  }

  /**
   * The cache value type, default java.lang.Object
   */
  public fun valueType(valueType: String) {
    it.property("valueType", valueType)
  }
}
