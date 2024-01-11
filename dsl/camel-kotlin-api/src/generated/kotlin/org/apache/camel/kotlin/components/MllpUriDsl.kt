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

public fun UriDsl.mllp(i: MllpUriDsl.() -> Unit) {
  MllpUriDsl(this).apply(i)
}

@CamelDslMarker
public class MllpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mllp")
  }

  private var hostname: String = ""

  private var port: String = ""

  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port")
  }

  public fun autoAck(autoAck: String) {
    it.property("autoAck", autoAck)
  }

  public fun autoAck(autoAck: Boolean) {
    it.property("autoAck", autoAck.toString())
  }

  public fun charsetName(charsetName: String) {
    it.property("charsetName", charsetName)
  }

  public fun hl7Headers(hl7Headers: String) {
    it.property("hl7Headers", hl7Headers)
  }

  public fun hl7Headers(hl7Headers: Boolean) {
    it.property("hl7Headers", hl7Headers.toString())
  }

  public fun requireEndOfData(requireEndOfData: String) {
    it.property("requireEndOfData", requireEndOfData)
  }

  public fun requireEndOfData(requireEndOfData: Boolean) {
    it.property("requireEndOfData", requireEndOfData.toString())
  }

  public fun stringPayload(stringPayload: String) {
    it.property("stringPayload", stringPayload)
  }

  public fun stringPayload(stringPayload: Boolean) {
    it.property("stringPayload", stringPayload.toString())
  }

  public fun validatePayload(validatePayload: String) {
    it.property("validatePayload", validatePayload)
  }

  public fun validatePayload(validatePayload: Boolean) {
    it.property("validatePayload", validatePayload.toString())
  }

  public fun acceptTimeout(acceptTimeout: String) {
    it.property("acceptTimeout", acceptTimeout)
  }

  public fun acceptTimeout(acceptTimeout: Int) {
    it.property("acceptTimeout", acceptTimeout.toString())
  }

  public fun backlog(backlog: String) {
    it.property("backlog", backlog)
  }

  public fun backlog(backlog: Int) {
    it.property("backlog", backlog.toString())
  }

  public fun bindRetryInterval(bindRetryInterval: String) {
    it.property("bindRetryInterval", bindRetryInterval)
  }

  public fun bindRetryInterval(bindRetryInterval: Int) {
    it.property("bindRetryInterval", bindRetryInterval.toString())
  }

  public fun bindTimeout(bindTimeout: String) {
    it.property("bindTimeout", bindTimeout)
  }

  public fun bindTimeout(bindTimeout: Int) {
    it.property("bindTimeout", bindTimeout.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun lenientBind(lenientBind: String) {
    it.property("lenientBind", lenientBind)
  }

  public fun lenientBind(lenientBind: Boolean) {
    it.property("lenientBind", lenientBind.toString())
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  public fun reuseAddress(reuseAddress: String) {
    it.property("reuseAddress", reuseAddress)
  }

  public fun reuseAddress(reuseAddress: Boolean) {
    it.property("reuseAddress", reuseAddress.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  public fun idleTimeoutStrategy(idleTimeoutStrategy: String) {
    it.property("idleTimeoutStrategy", idleTimeoutStrategy)
  }

  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun maxBufferSize(maxBufferSize: String) {
    it.property("maxBufferSize", maxBufferSize)
  }

  public fun maxBufferSize(maxBufferSize: Int) {
    it.property("maxBufferSize", maxBufferSize.toString())
  }

  public fun minBufferSize(minBufferSize: String) {
    it.property("minBufferSize", minBufferSize)
  }

  public fun minBufferSize(minBufferSize: Int) {
    it.property("minBufferSize", minBufferSize.toString())
  }

  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }

  public fun receiveBufferSize(receiveBufferSize: String) {
    it.property("receiveBufferSize", receiveBufferSize)
  }

  public fun receiveBufferSize(receiveBufferSize: Int) {
    it.property("receiveBufferSize", receiveBufferSize.toString())
  }

  public fun receiveTimeout(receiveTimeout: String) {
    it.property("receiveTimeout", receiveTimeout)
  }

  public fun receiveTimeout(receiveTimeout: Int) {
    it.property("receiveTimeout", receiveTimeout.toString())
  }

  public fun sendBufferSize(sendBufferSize: String) {
    it.property("sendBufferSize", sendBufferSize)
  }

  public fun sendBufferSize(sendBufferSize: Int) {
    it.property("sendBufferSize", sendBufferSize.toString())
  }

  public fun idleTimeout(idleTimeout: String) {
    it.property("idleTimeout", idleTimeout)
  }

  public fun idleTimeout(idleTimeout: Int) {
    it.property("idleTimeout", idleTimeout.toString())
  }
}
