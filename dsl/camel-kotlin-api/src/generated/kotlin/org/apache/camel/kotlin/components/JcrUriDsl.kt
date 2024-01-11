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

public fun UriDsl.jcr(i: JcrUriDsl.() -> Unit) {
  JcrUriDsl(this).apply(i)
}

@CamelDslMarker
public class JcrUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jcr")
  }

  private var host: String = ""

  private var base: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host/$base")
  }

  public fun base(base: String) {
    this.base = base
    it.url("$host/$base")
  }

  public fun deep(deep: String) {
    it.property("deep", deep)
  }

  public fun deep(deep: Boolean) {
    it.property("deep", deep.toString())
  }

  public fun eventTypes(eventTypes: String) {
    it.property("eventTypes", eventTypes)
  }

  public fun eventTypes(eventTypes: Int) {
    it.property("eventTypes", eventTypes.toString())
  }

  public fun nodeTypeNames(nodeTypeNames: String) {
    it.property("nodeTypeNames", nodeTypeNames)
  }

  public fun noLocal(noLocal: String) {
    it.property("noLocal", noLocal)
  }

  public fun noLocal(noLocal: Boolean) {
    it.property("noLocal", noLocal.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun sessionLiveCheckInterval(sessionLiveCheckInterval: String) {
    it.property("sessionLiveCheckInterval", sessionLiveCheckInterval)
  }

  public fun sessionLiveCheckIntervalOnStart(sessionLiveCheckIntervalOnStart: String) {
    it.property("sessionLiveCheckIntervalOnStart", sessionLiveCheckIntervalOnStart)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun uuids(uuids: String) {
    it.property("uuids", uuids)
  }

  public fun workspaceName(workspaceName: String) {
    it.property("workspaceName", workspaceName)
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
