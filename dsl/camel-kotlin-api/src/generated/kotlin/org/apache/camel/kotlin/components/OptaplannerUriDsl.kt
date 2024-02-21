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

public fun UriDsl.optaplanner(i: OptaplannerUriDsl.() -> Unit) {
  OptaplannerUriDsl(this).apply(i)
}

@CamelDslMarker
public class OptaplannerUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("optaplanner")
  }

  private var problemName: String = ""

  public fun problemName(problemName: String) {
    this.problemName = problemName
    it.url("$problemName")
  }

  public fun problemId(problemId: String) {
    it.property("problemId", problemId)
  }

  public fun problemId(problemId: Int) {
    it.property("problemId", problemId.toString())
  }

  public fun solverId(solverId: String) {
    it.property("solverId", solverId)
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

  public fun async(async: String) {
    it.property("async", async)
  }

  public fun async(async: Boolean) {
    it.property("async", async.toString())
  }

  public fun threadPoolSize(threadPoolSize: String) {
    it.property("threadPoolSize", threadPoolSize)
  }

  public fun threadPoolSize(threadPoolSize: Int) {
    it.property("threadPoolSize", threadPoolSize.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun configFile(configFile: String) {
    it.property("configFile", configFile)
  }

  public fun solverManager(solverManager: String) {
    it.property("solverManager", solverManager)
  }
}
