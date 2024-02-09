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

public fun UriDsl.seda(i: SedaUriDsl.() -> Unit) {
  SedaUriDsl(this).apply(i)
}

@CamelDslMarker
public class SedaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("seda")
  }

  private var name: String = ""

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun size(size: String) {
    it.property("size", size)
  }

  public fun size(size: Int) {
    it.property("size", size.toString())
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
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

  public fun limitConcurrentConsumers(limitConcurrentConsumers: String) {
    it.property("limitConcurrentConsumers", limitConcurrentConsumers)
  }

  public fun limitConcurrentConsumers(limitConcurrentConsumers: Boolean) {
    it.property("limitConcurrentConsumers", limitConcurrentConsumers.toString())
  }

  public fun multipleConsumers(multipleConsumers: String) {
    it.property("multipleConsumers", multipleConsumers)
  }

  public fun multipleConsumers(multipleConsumers: Boolean) {
    it.property("multipleConsumers", multipleConsumers.toString())
  }

  public fun pollTimeout(pollTimeout: String) {
    it.property("pollTimeout", pollTimeout)
  }

  public fun pollTimeout(pollTimeout: Int) {
    it.property("pollTimeout", pollTimeout.toString())
  }

  public fun purgeWhenStopping(purgeWhenStopping: String) {
    it.property("purgeWhenStopping", purgeWhenStopping)
  }

  public fun purgeWhenStopping(purgeWhenStopping: Boolean) {
    it.property("purgeWhenStopping", purgeWhenStopping.toString())
  }

  public fun blockWhenFull(blockWhenFull: String) {
    it.property("blockWhenFull", blockWhenFull)
  }

  public fun blockWhenFull(blockWhenFull: Boolean) {
    it.property("blockWhenFull", blockWhenFull.toString())
  }

  public fun discardIfNoConsumers(discardIfNoConsumers: String) {
    it.property("discardIfNoConsumers", discardIfNoConsumers)
  }

  public fun discardIfNoConsumers(discardIfNoConsumers: Boolean) {
    it.property("discardIfNoConsumers", discardIfNoConsumers.toString())
  }

  public fun discardWhenFull(discardWhenFull: String) {
    it.property("discardWhenFull", discardWhenFull)
  }

  public fun discardWhenFull(discardWhenFull: Boolean) {
    it.property("discardWhenFull", discardWhenFull.toString())
  }

  public fun failIfNoConsumers(failIfNoConsumers: String) {
    it.property("failIfNoConsumers", failIfNoConsumers)
  }

  public fun failIfNoConsumers(failIfNoConsumers: Boolean) {
    it.property("failIfNoConsumers", failIfNoConsumers.toString())
  }

  public fun offerTimeout(offerTimeout: String) {
    it.property("offerTimeout", offerTimeout)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun waitForTaskToComplete(waitForTaskToComplete: String) {
    it.property("waitForTaskToComplete", waitForTaskToComplete)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun queue(queue: String) {
    it.property("queue", queue)
  }
}
