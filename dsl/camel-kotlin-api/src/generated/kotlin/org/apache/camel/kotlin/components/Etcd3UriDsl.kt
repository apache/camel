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

public fun UriDsl.etcd3(i: Etcd3UriDsl.() -> Unit) {
  Etcd3UriDsl(this).apply(i)
}

@CamelDslMarker
public class Etcd3UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("etcd3")
  }

  private var path: String = ""

  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  public fun endpoints(endpoints: String) {
    it.property("endpoints", endpoints)
  }

  public fun keyCharset(keyCharset: String) {
    it.property("keyCharset", keyCharset)
  }

  public fun namespace(namespace: String) {
    it.property("namespace", namespace)
  }

  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  public fun prefix(prefix: Boolean) {
    it.property("prefix", prefix.toString())
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

  public fun fromIndex(fromIndex: String) {
    it.property("fromIndex", fromIndex)
  }

  public fun fromIndex(fromIndex: Int) {
    it.property("fromIndex", fromIndex.toString())
  }

  public fun valueCharset(valueCharset: String) {
    it.property("valueCharset", valueCharset)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun authHeaders(authHeaders: String) {
    it.property("authHeaders", authHeaders)
  }

  public fun authority(authority: String) {
    it.property("authority", authority)
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun headers(headers: String) {
    it.property("headers", headers)
  }

  public fun keepAliveTime(keepAliveTime: String) {
    it.property("keepAliveTime", keepAliveTime)
  }

  public fun keepAliveTimeout(keepAliveTimeout: String) {
    it.property("keepAliveTimeout", keepAliveTimeout)
  }

  public fun loadBalancerPolicy(loadBalancerPolicy: String) {
    it.property("loadBalancerPolicy", loadBalancerPolicy)
  }

  public fun maxInboundMessageSize(maxInboundMessageSize: String) {
    it.property("maxInboundMessageSize", maxInboundMessageSize)
  }

  public fun maxInboundMessageSize(maxInboundMessageSize: Int) {
    it.property("maxInboundMessageSize", maxInboundMessageSize.toString())
  }

  public fun retryDelay(retryDelay: String) {
    it.property("retryDelay", retryDelay)
  }

  public fun retryDelay(retryDelay: Int) {
    it.property("retryDelay", retryDelay.toString())
  }

  public fun retryMaxDelay(retryMaxDelay: String) {
    it.property("retryMaxDelay", retryMaxDelay)
  }

  public fun retryMaxDelay(retryMaxDelay: Int) {
    it.property("retryMaxDelay", retryMaxDelay.toString())
  }

  public fun retryMaxDuration(retryMaxDuration: String) {
    it.property("retryMaxDuration", retryMaxDuration)
  }

  public fun servicePath(servicePath: String) {
    it.property("servicePath", servicePath)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun sslContext(sslContext: String) {
    it.property("sslContext", sslContext)
  }

  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
