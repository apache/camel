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

public fun UriDsl.consul(i: ConsulUriDsl.() -> Unit) {
  ConsulUriDsl(this).apply(i)
}

@CamelDslMarker
public class ConsulUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("consul")
  }

  private var apiEndpoint: String = ""

  public fun apiEndpoint(apiEndpoint: String) {
    this.apiEndpoint = apiEndpoint
    it.url("$apiEndpoint")
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun consulClient(consulClient: String) {
    it.property("consulClient", consulClient)
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun pingInstance(pingInstance: String) {
    it.property("pingInstance", pingInstance)
  }

  public fun pingInstance(pingInstance: Boolean) {
    it.property("pingInstance", pingInstance.toString())
  }

  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  public fun tags(tags: String) {
    it.property("tags", tags)
  }

  public fun url(url: String) {
    it.property("url", url)
  }

  public fun writeTimeout(writeTimeout: String) {
    it.property("writeTimeout", writeTimeout)
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

  public fun action(action: String) {
    it.property("action", action)
  }

  public fun valueAsString(valueAsString: String) {
    it.property("valueAsString", valueAsString)
  }

  public fun valueAsString(valueAsString: Boolean) {
    it.property("valueAsString", valueAsString.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun consistencyMode(consistencyMode: String) {
    it.property("consistencyMode", consistencyMode)
  }

  public fun datacenter(datacenter: String) {
    it.property("datacenter", datacenter)
  }

  public fun nearNode(nearNode: String) {
    it.property("nearNode", nearNode)
  }

  public fun nodeMeta(nodeMeta: String) {
    it.property("nodeMeta", nodeMeta)
  }

  public fun aclToken(aclToken: String) {
    it.property("aclToken", aclToken)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun userName(userName: String) {
    it.property("userName", userName)
  }

  public fun blockSeconds(blockSeconds: String) {
    it.property("blockSeconds", blockSeconds)
  }

  public fun blockSeconds(blockSeconds: Int) {
    it.property("blockSeconds", blockSeconds.toString())
  }

  public fun firstIndex(firstIndex: String) {
    it.property("firstIndex", firstIndex)
  }

  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }
}
