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

  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  public fun cacheConfiguration(cacheConfiguration: String) {
    it.property("cacheConfiguration", cacheConfiguration)
  }

  public fun cacheConfigurationProperties(cacheConfigurationProperties: String) {
    it.property("cacheConfigurationProperties", cacheConfigurationProperties)
  }

  public fun cachingProvider(cachingProvider: String) {
    it.property("cachingProvider", cachingProvider)
  }

  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  public fun managementEnabled(managementEnabled: String) {
    it.property("managementEnabled", managementEnabled)
  }

  public fun managementEnabled(managementEnabled: Boolean) {
    it.property("managementEnabled", managementEnabled.toString())
  }

  public fun readThrough(readThrough: String) {
    it.property("readThrough", readThrough)
  }

  public fun readThrough(readThrough: Boolean) {
    it.property("readThrough", readThrough.toString())
  }

  public fun statisticsEnabled(statisticsEnabled: String) {
    it.property("statisticsEnabled", statisticsEnabled)
  }

  public fun statisticsEnabled(statisticsEnabled: Boolean) {
    it.property("statisticsEnabled", statisticsEnabled.toString())
  }

  public fun storeByValue(storeByValue: String) {
    it.property("storeByValue", storeByValue)
  }

  public fun storeByValue(storeByValue: Boolean) {
    it.property("storeByValue", storeByValue.toString())
  }

  public fun writeThrough(writeThrough: String) {
    it.property("writeThrough", writeThrough)
  }

  public fun writeThrough(writeThrough: Boolean) {
    it.property("writeThrough", writeThrough.toString())
  }

  public fun filteredEvents(filteredEvents: String) {
    it.property("filteredEvents", filteredEvents)
  }

  public fun oldValueRequired(oldValueRequired: String) {
    it.property("oldValueRequired", oldValueRequired)
  }

  public fun oldValueRequired(oldValueRequired: Boolean) {
    it.property("oldValueRequired", oldValueRequired.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun eventFilters(eventFilters: String) {
    it.property("eventFilters", eventFilters)
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun action(action: String) {
    it.property("action", action)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun cacheLoaderFactory(cacheLoaderFactory: String) {
    it.property("cacheLoaderFactory", cacheLoaderFactory)
  }

  public fun cacheWriterFactory(cacheWriterFactory: String) {
    it.property("cacheWriterFactory", cacheWriterFactory)
  }

  public fun createCacheIfNotExists(createCacheIfNotExists: String) {
    it.property("createCacheIfNotExists", createCacheIfNotExists)
  }

  public fun createCacheIfNotExists(createCacheIfNotExists: Boolean) {
    it.property("createCacheIfNotExists", createCacheIfNotExists.toString())
  }

  public fun expiryPolicyFactory(expiryPolicyFactory: String) {
    it.property("expiryPolicyFactory", expiryPolicyFactory)
  }

  public fun lookupProviders(lookupProviders: String) {
    it.property("lookupProviders", lookupProviders)
  }

  public fun lookupProviders(lookupProviders: Boolean) {
    it.property("lookupProviders", lookupProviders.toString())
  }
}
