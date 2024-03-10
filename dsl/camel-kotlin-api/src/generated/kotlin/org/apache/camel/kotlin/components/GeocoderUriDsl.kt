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

/**
 * Find geocodes (latitude and longitude) for a given address or the other way round.
 */
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

  /**
   * The geo address which should be prefixed with address:
   */
  public fun address(address: String) {
    this.address = address
    it.url("$address:$latlng")
  }

  /**
   * The geo latitude and longitude which should be prefixed with latlng:
   */
  public fun latlng(latlng: String) {
    this.latlng = latlng
    it.url("$address:$latlng")
  }

  /**
   * Whether to only enrich the Exchange with headers, and leave the body as-is.
   */
  public fun headersOnly(headersOnly: String) {
    it.property("headersOnly", headersOnly)
  }

  /**
   * Whether to only enrich the Exchange with headers, and leave the body as-is.
   */
  public fun headersOnly(headersOnly: Boolean) {
    it.property("headersOnly", headersOnly.toString())
  }

  /**
   * The language to use.
   */
  public fun language(language: String) {
    it.property("language", language)
  }

  /**
   * URL to the geocoder server. Mandatory for Nominatim server.
   */
  public fun serverUrl(serverUrl: String) {
    it.property("serverUrl", serverUrl)
  }

  /**
   * Type of GeoCoding server. Supported Nominatim and Google.
   */
  public fun type(type: String) {
    it.property("type", type)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * Proxy Authentication Domain to access Google GeoCoding server.
   */
  public fun proxyAuthDomain(proxyAuthDomain: String) {
    it.property("proxyAuthDomain", proxyAuthDomain)
  }

  /**
   * Proxy Authentication Host to access Google GeoCoding server.
   */
  public fun proxyAuthHost(proxyAuthHost: String) {
    it.property("proxyAuthHost", proxyAuthHost)
  }

  /**
   * Authentication Method to Google GeoCoding server.
   */
  public fun proxyAuthMethod(proxyAuthMethod: String) {
    it.property("proxyAuthMethod", proxyAuthMethod)
  }

  /**
   * Proxy Password to access GeoCoding server.
   */
  public fun proxyAuthPassword(proxyAuthPassword: String) {
    it.property("proxyAuthPassword", proxyAuthPassword)
  }

  /**
   * Proxy Username to access GeoCoding server.
   */
  public fun proxyAuthUsername(proxyAuthUsername: String) {
    it.property("proxyAuthUsername", proxyAuthUsername)
  }

  /**
   * Proxy Host to access GeoCoding server.
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * Proxy Port to access GeoCoding server.
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * Proxy Port to access GeoCoding server.
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * API Key to access Google. Mandatory for Google GeoCoding server.
   */
  public fun apiKey(apiKey: String) {
    it.property("apiKey", apiKey)
  }

  /**
   * Client ID to access Google GeoCoding server.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * Client Key to access Google GeoCoding server.
   */
  public fun clientKey(clientKey: String) {
    it.property("clientKey", clientKey)
  }
}
