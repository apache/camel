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

public fun UriDsl.pgevent(i: PgeventUriDsl.() -> Unit) {
  PgeventUriDsl(this).apply(i)
}

@CamelDslMarker
public class PgeventUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("pgevent")
  }

  private var host: String = ""

  private var port: String = ""

  private var database: String = ""

  private var channel: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$database/$channel")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$database/$channel")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$database/$channel")
  }

  public fun database(database: String) {
    this.database = database
    it.url("$host:$port/$database/$channel")
  }

  public fun channel(channel: String) {
    this.channel = channel
    it.url("$host:$port/$database/$channel")
  }

  public fun datasource(datasource: String) {
    it.property("datasource", datasource)
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

  public fun pass(pass: String) {
    it.property("pass", pass)
  }

  public fun user(user: String) {
    it.property("user", user)
  }
}
