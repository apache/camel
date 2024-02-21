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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.`reactive-streams`(i: ReactiveStreamsUriDsl.() -> Unit) {
  ReactiveStreamsUriDsl(this).apply(i)
}

@CamelDslMarker
public class ReactiveStreamsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("reactive-streams")
  }

  private var stream: String = ""

  public fun stream(stream: String) {
    this.stream = stream
    it.url("$stream")
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun exchangesRefillLowWatermark(exchangesRefillLowWatermark: String) {
    it.property("exchangesRefillLowWatermark", exchangesRefillLowWatermark)
  }

  public fun exchangesRefillLowWatermark(exchangesRefillLowWatermark: Double) {
    it.property("exchangesRefillLowWatermark", exchangesRefillLowWatermark.toString())
  }

  public fun forwardOnComplete(forwardOnComplete: String) {
    it.property("forwardOnComplete", forwardOnComplete)
  }

  public fun forwardOnComplete(forwardOnComplete: Boolean) {
    it.property("forwardOnComplete", forwardOnComplete.toString())
  }

  public fun forwardOnError(forwardOnError: String) {
    it.property("forwardOnError", forwardOnError)
  }

  public fun forwardOnError(forwardOnError: Boolean) {
    it.property("forwardOnError", forwardOnError.toString())
  }

  public fun maxInflightExchanges(maxInflightExchanges: String) {
    it.property("maxInflightExchanges", maxInflightExchanges)
  }

  public fun maxInflightExchanges(maxInflightExchanges: Int) {
    it.property("maxInflightExchanges", maxInflightExchanges.toString())
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

  public fun backpressureStrategy(backpressureStrategy: String) {
    it.property("backpressureStrategy", backpressureStrategy)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
