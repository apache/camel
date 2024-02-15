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

  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  public fun queryBuilder(queryBuilder: String) {
    it.property("queryBuilder", queryBuilder)
  }

  public fun clusteredListener(clusteredListener: String) {
    it.property("clusteredListener", clusteredListener)
  }

  public fun clusteredListener(clusteredListener: Boolean) {
    it.property("clusteredListener", clusteredListener.toString())
  }

  public fun customListener(customListener: String) {
    it.property("customListener", customListener)
  }

  public fun eventTypes(eventTypes: String) {
    it.property("eventTypes", eventTypes)
  }

  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun defaultValue(defaultValue: String) {
    it.property("defaultValue", defaultValue)
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun oldValue(oldValue: String) {
    it.property("oldValue", oldValue)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun `value`(`value`: String) {
    it.property("value", value)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun cacheContainer(cacheContainer: String) {
    it.property("cacheContainer", cacheContainer)
  }

  public fun cacheContainerConfiguration(cacheContainerConfiguration: String) {
    it.property("cacheContainerConfiguration", cacheContainerConfiguration)
  }

  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  public fun flags(flags: String) {
    it.property("flags", flags)
  }

  public fun remappingFunction(remappingFunction: String) {
    it.property("remappingFunction", remappingFunction)
  }

  public fun resultHeader(resultHeader: String) {
    it.property("resultHeader", resultHeader)
  }
}
