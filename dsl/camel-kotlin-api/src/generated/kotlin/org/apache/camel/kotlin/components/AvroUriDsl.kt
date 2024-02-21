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

public fun UriDsl.avro(i: AvroUriDsl.() -> Unit) {
  AvroUriDsl(this).apply(i)
}

@CamelDslMarker
public class AvroUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("avro")
  }

  private var transport: String = ""

  private var port: String = ""

  private var host: String = ""

  private var messageName: String = ""

  public fun transport(transport: String) {
    this.transport = transport
    it.url("$transport:$host:$port/$messageName")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$transport:$host:$port/$messageName")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$transport:$host:$port/$messageName")
  }

  public fun host(host: String) {
    this.host = host
    it.url("$transport:$host:$port/$messageName")
  }

  public fun messageName(messageName: String) {
    this.messageName = messageName
    it.url("$transport:$host:$port/$messageName")
  }

  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  public fun protocolClassName(protocolClassName: String) {
    it.property("protocolClassName", protocolClassName)
  }

  public fun protocolLocation(protocolLocation: String) {
    it.property("protocolLocation", protocolLocation)
  }

  public fun reflectionProtocol(reflectionProtocol: String) {
    it.property("reflectionProtocol", reflectionProtocol)
  }

  public fun reflectionProtocol(reflectionProtocol: Boolean) {
    it.property("reflectionProtocol", reflectionProtocol.toString())
  }

  public fun singleParameter(singleParameter: String) {
    it.property("singleParameter", singleParameter)
  }

  public fun singleParameter(singleParameter: Boolean) {
    it.property("singleParameter", singleParameter.toString())
  }

  public fun uriAuthority(uriAuthority: String) {
    it.property("uriAuthority", uriAuthority)
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
