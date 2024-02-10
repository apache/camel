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

public fun UriDsl.coap(i: CoapUriDsl.() -> Unit) {
  CoapUriDsl(this).apply(i)
}

@CamelDslMarker
public class CoapUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("coap")
  }

  private var uri: String = ""

  public fun uri(uri: String) {
    this.uri = uri
    it.url("$uri")
  }

  public fun coapMethodRestrict(coapMethodRestrict: String) {
    it.property("coapMethodRestrict", coapMethodRestrict)
  }

  public fun observable(observable: String) {
    it.property("observable", observable)
  }

  public fun observable(observable: Boolean) {
    it.property("observable", observable.toString())
  }

  public fun observe(observe: String) {
    it.property("observe", observe)
  }

  public fun observe(observe: Boolean) {
    it.property("observe", observe.toString())
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

  public fun notify(notify: String) {
    it.property("notify", notify)
  }

  public fun notify(notify: Boolean) {
    it.property("notify", notify.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun advancedCertificateVerifier(advancedCertificateVerifier: String) {
    it.property("advancedCertificateVerifier", advancedCertificateVerifier)
  }

  public fun advancedPskStore(advancedPskStore: String) {
    it.property("advancedPskStore", advancedPskStore)
  }

  public fun alias(alias: String) {
    it.property("alias", alias)
  }

  public fun cipherSuites(cipherSuites: String) {
    it.property("cipherSuites", cipherSuites)
  }

  public fun clientAuthentication(clientAuthentication: String) {
    it.property("clientAuthentication", clientAuthentication)
  }

  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }

  public fun recommendedCipherSuitesOnly(recommendedCipherSuitesOnly: String) {
    it.property("recommendedCipherSuitesOnly", recommendedCipherSuitesOnly)
  }

  public fun recommendedCipherSuitesOnly(recommendedCipherSuitesOnly: Boolean) {
    it.property("recommendedCipherSuitesOnly", recommendedCipherSuitesOnly.toString())
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
