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

  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: String) {
    it.property("propagateIncomingBodyIfNoReturnValue", propagateIncomingBodyIfNoReturnValue)
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: Boolean) {
    it.property("propagateIncomingBodyIfNoReturnValue",
        propagateIncomingBodyIfNoReturnValue.toString())
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: String) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects)
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: Boolean) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects.toString())
  }

  public fun autoUnsubscribe(autoUnsubscribe: String) {
    it.property("autoUnsubscribe", autoUnsubscribe)
  }

  public fun autoUnsubscribe(autoUnsubscribe: Boolean) {
    it.property("autoUnsubscribe", autoUnsubscribe.toString())
  }

  public fun fireExistingQueryResults(fireExistingQueryResults: String) {
    it.property("fireExistingQueryResults", fireExistingQueryResults)
  }

  public fun fireExistingQueryResults(fireExistingQueryResults: Boolean) {
    it.property("fireExistingQueryResults", fireExistingQueryResults.toString())
  }

  public fun oneExchangePerUpdate(oneExchangePerUpdate: String) {
    it.property("oneExchangePerUpdate", oneExchangePerUpdate)
  }

  public fun oneExchangePerUpdate(oneExchangePerUpdate: Boolean) {
    it.property("oneExchangePerUpdate", oneExchangePerUpdate.toString())
  }

  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  public fun pageSize(pageSize: Int) {
    it.property("pageSize", pageSize.toString())
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun remoteFilter(remoteFilter: String) {
    it.property("remoteFilter", remoteFilter)
  }

  public fun timeInterval(timeInterval: String) {
    it.property("timeInterval", timeInterval)
  }

  public fun timeInterval(timeInterval: Int) {
    it.property("timeInterval", timeInterval.toString())
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

  public fun cachePeekMode(cachePeekMode: String) {
    it.property("cachePeekMode", cachePeekMode)
  }

  public fun failIfInexistentCache(failIfInexistentCache: String) {
    it.property("failIfInexistentCache", failIfInexistentCache)
  }

  public fun failIfInexistentCache(failIfInexistentCache: Boolean) {
    it.property("failIfInexistentCache", failIfInexistentCache.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
