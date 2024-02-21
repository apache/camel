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

public fun UriDsl.thrift(i: ThriftUriDsl.() -> Unit) {
  ThriftUriDsl(this).apply(i)
}

@CamelDslMarker
public class ThriftUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("thrift")
  }

  private var host: String = ""

  private var port: String = ""

  private var service: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$service")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$service")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$service")
  }

  public fun service(service: String) {
    this.service = service
    it.url("$host:$port/$service")
  }

  public fun compressionType(compressionType: String) {
    it.property("compressionType", compressionType)
  }

  public fun exchangeProtocol(exchangeProtocol: String) {
    it.property("exchangeProtocol", exchangeProtocol)
  }

  public fun clientTimeout(clientTimeout: String) {
    it.property("clientTimeout", clientTimeout)
  }

  public fun clientTimeout(clientTimeout: Int) {
    it.property("clientTimeout", clientTimeout.toString())
  }

  public fun maxPoolSize(maxPoolSize: String) {
    it.property("maxPoolSize", maxPoolSize)
  }

  public fun maxPoolSize(maxPoolSize: Int) {
    it.property("maxPoolSize", maxPoolSize.toString())
  }

  public fun poolSize(poolSize: String) {
    it.property("poolSize", poolSize)
  }

  public fun poolSize(poolSize: Int) {
    it.property("poolSize", poolSize.toString())
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

  public fun method(method: String) {
    it.property("method", method)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun negotiationType(negotiationType: String) {
    it.property("negotiationType", negotiationType)
  }

  public fun sslParameters(sslParameters: String) {
    it.property("sslParameters", sslParameters)
  }
}
