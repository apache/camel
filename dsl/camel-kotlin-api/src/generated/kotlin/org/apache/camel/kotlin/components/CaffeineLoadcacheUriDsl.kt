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

  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  public fun action(action: String) {
    it.property("action", action)
  }

  public fun createCacheIfNotExist(createCacheIfNotExist: String) {
    it.property("createCacheIfNotExist", createCacheIfNotExist)
  }

  public fun createCacheIfNotExist(createCacheIfNotExist: Boolean) {
    it.property("createCacheIfNotExist", createCacheIfNotExist.toString())
  }

  public fun evictionType(evictionType: String) {
    it.property("evictionType", evictionType)
  }

  public fun expireAfterAccessTime(expireAfterAccessTime: String) {
    it.property("expireAfterAccessTime", expireAfterAccessTime)
  }

  public fun expireAfterAccessTime(expireAfterAccessTime: Int) {
    it.property("expireAfterAccessTime", expireAfterAccessTime.toString())
  }

  public fun expireAfterWriteTime(expireAfterWriteTime: String) {
    it.property("expireAfterWriteTime", expireAfterWriteTime)
  }

  public fun expireAfterWriteTime(expireAfterWriteTime: Int) {
    it.property("expireAfterWriteTime", expireAfterWriteTime.toString())
  }

  public fun initialCapacity(initialCapacity: String) {
    it.property("initialCapacity", initialCapacity)
  }

  public fun initialCapacity(initialCapacity: Int) {
    it.property("initialCapacity", initialCapacity.toString())
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun maximumSize(maximumSize: String) {
    it.property("maximumSize", maximumSize)
  }

  public fun maximumSize(maximumSize: Int) {
    it.property("maximumSize", maximumSize.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun cacheLoader(cacheLoader: String) {
    it.property("cacheLoader", cacheLoader)
  }

  public fun removalListener(removalListener: String) {
    it.property("removalListener", removalListener)
  }

  public fun statsCounter(statsCounter: String) {
    it.property("statsCounter", statsCounter)
  }

  public fun statsEnabled(statsEnabled: String) {
    it.property("statsEnabled", statsEnabled)
  }

  public fun statsEnabled(statsEnabled: Boolean) {
    it.property("statsEnabled", statsEnabled.toString())
  }

  public fun valueType(valueType: String) {
    it.property("valueType", valueType)
  }
}
