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

public fun UriDsl.`vertx-http`(i: VertxHttpUriDsl.() -> Unit) {
  VertxHttpUriDsl(this).apply(i)
}

@CamelDslMarker
public class VertxHttpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("vertx-http")
  }

  private var httpUri: String = ""

  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("$httpUri")
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  public fun cookieStore(cookieStore: String) {
    it.property("cookieStore", cookieStore)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun httpMethod(httpMethod: String) {
    it.property("httpMethod", httpMethod)
  }

  public fun okStatusCodeRange(okStatusCodeRange: String) {
    it.property("okStatusCodeRange", okStatusCodeRange)
  }

  public fun responsePayloadAsByteArray(responsePayloadAsByteArray: String) {
    it.property("responsePayloadAsByteArray", responsePayloadAsByteArray)
  }

  public fun responsePayloadAsByteArray(responsePayloadAsByteArray: Boolean) {
    it.property("responsePayloadAsByteArray", responsePayloadAsByteArray.toString())
  }

  public fun sessionManagement(sessionManagement: String) {
    it.property("sessionManagement", sessionManagement)
  }

  public fun sessionManagement(sessionManagement: Boolean) {
    it.property("sessionManagement", sessionManagement.toString())
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  public fun useCompression(useCompression: String) {
    it.property("useCompression", useCompression)
  }

  public fun useCompression(useCompression: Boolean) {
    it.property("useCompression", useCompression.toString())
  }

  public fun vertxHttpBinding(vertxHttpBinding: String) {
    it.property("vertxHttpBinding", vertxHttpBinding)
  }

  public fun webClientOptions(webClientOptions: String) {
    it.property("webClientOptions", webClientOptions)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun proxyType(proxyType: String) {
    it.property("proxyType", proxyType)
  }

  public fun proxyUsername(proxyUsername: String) {
    it.property("proxyUsername", proxyUsername)
  }

  public fun basicAuthPassword(basicAuthPassword: String) {
    it.property("basicAuthPassword", basicAuthPassword)
  }

  public fun basicAuthUsername(basicAuthUsername: String) {
    it.property("basicAuthUsername", basicAuthUsername)
  }

  public fun bearerToken(bearerToken: String) {
    it.property("bearerToken", bearerToken)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
