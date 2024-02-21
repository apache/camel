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

public fun UriDsl.braintree(i: BraintreeUriDsl.() -> Unit) {
  BraintreeUriDsl(this).apply(i)
}

@CamelDslMarker
public class BraintreeUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("braintree")
  }

  private var apiName: String = ""

  private var methodName: String = ""

  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  public fun environment(environment: String) {
    it.property("environment", environment)
  }

  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  public fun merchantId(merchantId: String) {
    it.property("merchantId", merchantId)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun httpReadTimeout(httpReadTimeout: String) {
    it.property("httpReadTimeout", httpReadTimeout)
  }

  public fun httpReadTimeout(httpReadTimeout: Int) {
    it.property("httpReadTimeout", httpReadTimeout.toString())
  }

  public fun httpLogLevel(httpLogLevel: String) {
    it.property("httpLogLevel", httpLogLevel)
  }

  public fun httpLogName(httpLogName: String) {
    it.property("httpLogName", httpLogName)
  }

  public fun logHandlerEnabled(logHandlerEnabled: String) {
    it.property("logHandlerEnabled", logHandlerEnabled)
  }

  public fun logHandlerEnabled(logHandlerEnabled: Boolean) {
    it.property("logHandlerEnabled", logHandlerEnabled.toString())
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }
}
