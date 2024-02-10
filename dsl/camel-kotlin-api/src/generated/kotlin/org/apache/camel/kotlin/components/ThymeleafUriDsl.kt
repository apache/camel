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

public fun UriDsl.thymeleaf(i: ThymeleafUriDsl.() -> Unit) {
  ThymeleafUriDsl(this).apply(i)
}

@CamelDslMarker
public class ThymeleafUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("thymeleaf")
  }

  private var resourceUri: String = ""

  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  public fun allowContextMapAll(allowContextMapAll: String) {
    it.property("allowContextMapAll", allowContextMapAll)
  }

  public fun allowContextMapAll(allowContextMapAll: Boolean) {
    it.property("allowContextMapAll", allowContextMapAll.toString())
  }

  public fun cacheable(cacheable: String) {
    it.property("cacheable", cacheable)
  }

  public fun cacheable(cacheable: Boolean) {
    it.property("cacheable", cacheable.toString())
  }

  public fun cacheTimeToLive(cacheTimeToLive: String) {
    it.property("cacheTimeToLive", cacheTimeToLive)
  }

  public fun cacheTimeToLive(cacheTimeToLive: Int) {
    it.property("cacheTimeToLive", cacheTimeToLive.toString())
  }

  public fun checkExistence(checkExistence: String) {
    it.property("checkExistence", checkExistence)
  }

  public fun checkExistence(checkExistence: Boolean) {
    it.property("checkExistence", checkExistence.toString())
  }

  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  public fun templateMode(templateMode: String) {
    it.property("templateMode", templateMode)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun order(order: String) {
    it.property("order", order)
  }

  public fun order(order: Int) {
    it.property("order", order.toString())
  }

  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  public fun resolver(resolver: String) {
    it.property("resolver", resolver)
  }

  public fun suffix(suffix: String) {
    it.property("suffix", suffix)
  }
}
