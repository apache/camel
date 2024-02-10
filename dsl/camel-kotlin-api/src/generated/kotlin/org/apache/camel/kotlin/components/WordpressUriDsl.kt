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

public fun UriDsl.wordpress(i: WordpressUriDsl.() -> Unit) {
  WordpressUriDsl(this).apply(i)
}

@CamelDslMarker
public class WordpressUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("wordpress")
  }

  private var operation: String = ""

  private var operationDetail: String = ""

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun operationDetail(operationDetail: String) {
    this.operationDetail = operationDetail
    it.url("$operation")
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun criteria(criteria: String) {
    it.property("criteria", criteria)
  }

  public fun force(force: String) {
    it.property("force", force)
  }

  public fun force(force: Boolean) {
    it.property("force", force.toString())
  }

  public fun id(id: String) {
    it.property("id", id)
  }

  public fun id(id: Int) {
    it.property("id", id.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun searchCriteria(searchCriteria: String) {
    it.property("searchCriteria", searchCriteria)
  }

  public fun url(url: String) {
    it.property("url", url)
  }

  public fun user(user: String) {
    it.property("user", user)
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
