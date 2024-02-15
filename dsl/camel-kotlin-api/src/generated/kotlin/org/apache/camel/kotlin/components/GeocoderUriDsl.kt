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

public fun UriDsl.geocoder(i: GeocoderUriDsl.() -> Unit) {
  GeocoderUriDsl(this).apply(i)
}

@CamelDslMarker
public class GeocoderUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("geocoder")
  }

  private var address: String = ""

  private var latlng: String = ""

  public fun address(address: String) {
    this.address = address
    it.url("$address:$latlng")
  }

  public fun latlng(latlng: String) {
    this.latlng = latlng
    it.url("$address:$latlng")
  }

  public fun headersOnly(headersOnly: String) {
    it.property("headersOnly", headersOnly)
  }

  public fun headersOnly(headersOnly: Boolean) {
    it.property("headersOnly", headersOnly.toString())
  }

  public fun language(language: String) {
    it.property("language", language)
  }

  public fun serverUrl(serverUrl: String) {
    it.property("serverUrl", serverUrl)
  }

  public fun type(type: String) {
    it.property("type", type)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun proxyAuthDomain(proxyAuthDomain: String) {
    it.property("proxyAuthDomain", proxyAuthDomain)
  }

  public fun proxyAuthHost(proxyAuthHost: String) {
    it.property("proxyAuthHost", proxyAuthHost)
  }

  public fun proxyAuthMethod(proxyAuthMethod: String) {
    it.property("proxyAuthMethod", proxyAuthMethod)
  }

  public fun proxyAuthPassword(proxyAuthPassword: String) {
    it.property("proxyAuthPassword", proxyAuthPassword)
  }

  public fun proxyAuthUsername(proxyAuthUsername: String) {
    it.property("proxyAuthUsername", proxyAuthUsername)
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

  public fun apiKey(apiKey: String) {
    it.property("apiKey", apiKey)
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun clientKey(clientKey: String) {
    it.property("clientKey", clientKey)
  }
}
