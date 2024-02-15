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

public fun UriDsl.`dynamic-router`(i: DynamicRouterUriDsl.() -> Unit) {
  DynamicRouterUriDsl(this).apply(i)
}

@CamelDslMarker
public class DynamicRouterUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dynamic-router")
  }

  private var channel: String = ""

  public fun channel(channel: String) {
    this.channel = channel
    it.url("$channel")
  }

  public fun aggregationStrategy(aggregationStrategy: String) {
    it.property("aggregationStrategy", aggregationStrategy)
  }

  public fun aggregationStrategyBean(aggregationStrategyBean: String) {
    it.property("aggregationStrategyBean", aggregationStrategyBean)
  }

  public fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: String) {
    it.property("aggregationStrategyMethodAllowNull", aggregationStrategyMethodAllowNull)
  }

  public fun aggregationStrategyMethodAllowNull(aggregationStrategyMethodAllowNull: Boolean) {
    it.property("aggregationStrategyMethodAllowNull", aggregationStrategyMethodAllowNull.toString())
  }

  public fun aggregationStrategyMethodName(aggregationStrategyMethodName: String) {
    it.property("aggregationStrategyMethodName", aggregationStrategyMethodName)
  }

  public fun cacheSize(cacheSize: String) {
    it.property("cacheSize", cacheSize)
  }

  public fun cacheSize(cacheSize: Int) {
    it.property("cacheSize", cacheSize.toString())
  }

  public fun executorService(executorService: String) {
    it.property("executorService", executorService)
  }

  public fun executorServiceBean(executorServiceBean: String) {
    it.property("executorServiceBean", executorServiceBean)
  }

  public fun ignoreInvalidEndpoints(ignoreInvalidEndpoints: String) {
    it.property("ignoreInvalidEndpoints", ignoreInvalidEndpoints)
  }

  public fun ignoreInvalidEndpoints(ignoreInvalidEndpoints: Boolean) {
    it.property("ignoreInvalidEndpoints", ignoreInvalidEndpoints.toString())
  }

  public fun onPrepare(onPrepare: String) {
    it.property("onPrepare", onPrepare)
  }

  public fun onPrepareProcessor(onPrepareProcessor: String) {
    it.property("onPrepareProcessor", onPrepareProcessor)
  }

  public fun parallelAggregate(parallelAggregate: String) {
    it.property("parallelAggregate", parallelAggregate)
  }

  public fun parallelAggregate(parallelAggregate: Boolean) {
    it.property("parallelAggregate", parallelAggregate.toString())
  }

  public fun parallelProcessing(parallelProcessing: String) {
    it.property("parallelProcessing", parallelProcessing)
  }

  public fun parallelProcessing(parallelProcessing: Boolean) {
    it.property("parallelProcessing", parallelProcessing.toString())
  }

  public fun recipientMode(recipientMode: String) {
    it.property("recipientMode", recipientMode)
  }

  public fun shareUnitOfWork(shareUnitOfWork: String) {
    it.property("shareUnitOfWork", shareUnitOfWork)
  }

  public fun shareUnitOfWork(shareUnitOfWork: Boolean) {
    it.property("shareUnitOfWork", shareUnitOfWork.toString())
  }

  public fun stopOnException(stopOnException: String) {
    it.property("stopOnException", stopOnException)
  }

  public fun stopOnException(stopOnException: Boolean) {
    it.property("stopOnException", stopOnException.toString())
  }

  public fun streaming(streaming: String) {
    it.property("streaming", streaming)
  }

  public fun streaming(streaming: Boolean) {
    it.property("streaming", streaming.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun warnDroppedMessage(warnDroppedMessage: String) {
    it.property("warnDroppedMessage", warnDroppedMessage)
  }

  public fun warnDroppedMessage(warnDroppedMessage: Boolean) {
    it.property("warnDroppedMessage", warnDroppedMessage.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
