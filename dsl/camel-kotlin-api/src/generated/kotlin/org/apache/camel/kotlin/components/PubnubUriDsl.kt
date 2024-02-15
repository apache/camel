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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.pubnub(i: PubnubUriDsl.() -> Unit) {
  PubnubUriDsl(this).apply(i)
}

@CamelDslMarker
public class PubnubUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("pubnub")
  }

  private var channel: String = ""

  public fun channel(channel: String) {
    this.channel = channel
    it.url("$channel")
  }

  public fun uuid(uuid: String) {
    it.property("uuid", uuid)
  }

  public fun withPresence(withPresence: String) {
    it.property("withPresence", withPresence)
  }

  public fun withPresence(withPresence: Boolean) {
    it.property("withPresence", withPresence.toString())
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

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun pubnub(pubnub: String) {
    it.property("pubnub", pubnub)
  }

  public fun authKey(authKey: String) {
    it.property("authKey", authKey)
  }

  public fun cipherKey(cipherKey: String) {
    it.property("cipherKey", cipherKey)
  }

  public fun publishKey(publishKey: String) {
    it.property("publishKey", publishKey)
  }

  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  public fun subscribeKey(subscribeKey: String) {
    it.property("subscribeKey", subscribeKey)
  }
}
