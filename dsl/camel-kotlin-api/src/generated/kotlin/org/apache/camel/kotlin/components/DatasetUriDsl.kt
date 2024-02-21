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

public fun UriDsl.dataset(i: DatasetUriDsl.() -> Unit) {
  DatasetUriDsl(this).apply(i)
}

@CamelDslMarker
public class DatasetUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dataset")
  }

  private var name: String = ""

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun dataSetIndex(dataSetIndex: String) {
    it.property("dataSetIndex", dataSetIndex)
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun minRate(minRate: String) {
    it.property("minRate", minRate)
  }

  public fun minRate(minRate: Int) {
    it.property("minRate", minRate.toString())
  }

  public fun preloadSize(preloadSize: String) {
    it.property("preloadSize", preloadSize)
  }

  public fun preloadSize(preloadSize: Int) {
    it.property("preloadSize", preloadSize.toString())
  }

  public fun produceDelay(produceDelay: String) {
    it.property("produceDelay", produceDelay)
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

  public fun assertPeriod(assertPeriod: String) {
    it.property("assertPeriod", assertPeriod)
  }

  public fun consumeDelay(consumeDelay: String) {
    it.property("consumeDelay", consumeDelay)
  }

  public fun expectedCount(expectedCount: String) {
    it.property("expectedCount", expectedCount)
  }

  public fun expectedCount(expectedCount: Int) {
    it.property("expectedCount", expectedCount.toString())
  }

  public fun failFast(failFast: String) {
    it.property("failFast", failFast)
  }

  public fun failFast(failFast: Boolean) {
    it.property("failFast", failFast.toString())
  }

  public fun log(log: String) {
    it.property("log", log)
  }

  public fun log(log: Boolean) {
    it.property("log", log.toString())
  }

  public fun reportGroup(reportGroup: String) {
    it.property("reportGroup", reportGroup)
  }

  public fun reportGroup(reportGroup: Int) {
    it.property("reportGroup", reportGroup.toString())
  }

  public fun resultMinimumWaitTime(resultMinimumWaitTime: String) {
    it.property("resultMinimumWaitTime", resultMinimumWaitTime)
  }

  public fun resultWaitTime(resultWaitTime: String) {
    it.property("resultWaitTime", resultWaitTime)
  }

  public fun retainFirst(retainFirst: String) {
    it.property("retainFirst", retainFirst)
  }

  public fun retainFirst(retainFirst: Int) {
    it.property("retainFirst", retainFirst.toString())
  }

  public fun retainLast(retainLast: String) {
    it.property("retainLast", retainLast)
  }

  public fun retainLast(retainLast: Int) {
    it.property("retainLast", retainLast.toString())
  }

  public fun sleepForEmptyTest(sleepForEmptyTest: String) {
    it.property("sleepForEmptyTest", sleepForEmptyTest)
  }

  public fun copyOnExchange(copyOnExchange: String) {
    it.property("copyOnExchange", copyOnExchange)
  }

  public fun copyOnExchange(copyOnExchange: Boolean) {
    it.property("copyOnExchange", copyOnExchange.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
