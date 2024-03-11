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
 * Send requests to external HTTP servers using Vert.x
 */
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

  /**
   * The HTTP URI to connect to
   */
  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("$httpUri")
  }

  /**
   * The amount of time in milliseconds until a connection is established. A timeout value of zero
   * is interpreted as an infinite timeout.
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * The amount of time in milliseconds until a connection is established. A timeout value of zero
   * is interpreted as an infinite timeout.
   */
  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  /**
   * A custom CookieStore to use when session management is enabled. If this option is not set then
   * an in-memory CookieStore is used
   */
  public fun cookieStore(cookieStore: String) {
    it.property("cookieStore", cookieStore)
  }

  /**
   * A custom org.apache.camel.spi.HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * The HTTP method to use. The HttpMethod header cannot override this option if set
   */
  public fun httpMethod(httpMethod: String) {
    it.property("httpMethod", httpMethod)
  }

  /**
   * The status codes which are considered a success response. The values are inclusive. Multiple
   * ranges can be defined, separated by comma, e.g. 200-204,209,301-304. Each range must be a single
   * number or from-to with the dash included
   */
  public fun okStatusCodeRange(okStatusCodeRange: String) {
    it.property("okStatusCodeRange", okStatusCodeRange)
  }

  /**
   * Whether the response body should be byte or as io.vertx.core.buffer.Buffer
   */
  public fun responsePayloadAsByteArray(responsePayloadAsByteArray: String) {
    it.property("responsePayloadAsByteArray", responsePayloadAsByteArray)
  }

  /**
   * Whether the response body should be byte or as io.vertx.core.buffer.Buffer
   */
  public fun responsePayloadAsByteArray(responsePayloadAsByteArray: Boolean) {
    it.property("responsePayloadAsByteArray", responsePayloadAsByteArray.toString())
  }

  /**
   * Enables session management via WebClientSession. By default the client is configured to use an
   * in-memory CookieStore. The cookieStore option can be used to override this
   */
  public fun sessionManagement(sessionManagement: String) {
    it.property("sessionManagement", sessionManagement)
  }

  /**
   * Enables session management via WebClientSession. By default the client is configured to use an
   * in-memory CookieStore. The cookieStore option can be used to override this
   */
  public fun sessionManagement(sessionManagement: Boolean) {
    it.property("sessionManagement", sessionManagement.toString())
  }

  /**
   * Disable throwing HttpOperationFailedException in case of failed responses from the remote
   * server
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  /**
   * Disable throwing HttpOperationFailedException in case of failed responses from the remote
   * server
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  /**
   * The amount of time in milliseconds after which if the request does not return any data within
   * the timeout period a TimeoutException fails the request. Setting zero or a negative value disables
   * the timeout.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * The amount of time in milliseconds after which if the request does not return any data within
   * the timeout period a TimeoutException fails the request. Setting zero or a negative value disables
   * the timeout.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception
   * was sent back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is, instead of
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Camel will deserialize the incoming data
   * from the request to a Java object, which can be a potential security risk.
   */
  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception
   * was sent back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is, instead of
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Camel will deserialize the incoming data
   * from the request to a Java object, which can be a potential security risk.
   */
  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  /**
   * Set whether compression is enabled to handled compressed (E.g gzipped) responses
   */
  public fun useCompression(useCompression: String) {
    it.property("useCompression", useCompression)
  }

  /**
   * Set whether compression is enabled to handled compressed (E.g gzipped) responses
   */
  public fun useCompression(useCompression: Boolean) {
    it.property("useCompression", useCompression.toString())
  }

  /**
   * A custom VertxHttpBinding which can control how to bind between Vert.x and Camel.
   */
  public fun vertxHttpBinding(vertxHttpBinding: String) {
    it.property("vertxHttpBinding", vertxHttpBinding)
  }

  /**
   * Sets customized options for configuring the Vert.x WebClient
   */
  public fun webClientOptions(webClientOptions: String) {
    it.property("webClientOptions", webClientOptions)
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
   * The proxy server host address
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * The proxy server password if authentication is required
   */
  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  /**
   * The proxy server port
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * The proxy server port
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * The proxy server type
   */
  public fun proxyType(proxyType: String) {
    it.property("proxyType", proxyType)
  }

  /**
   * The proxy server username if authentication is required
   */
  public fun proxyUsername(proxyUsername: String) {
    it.property("proxyUsername", proxyUsername)
  }

  /**
   * The password to use for basic authentication
   */
  public fun basicAuthPassword(basicAuthPassword: String) {
    it.property("basicAuthPassword", basicAuthPassword)
  }

  /**
   * The user name to use for basic authentication
   */
  public fun basicAuthUsername(basicAuthUsername: String) {
    it.property("basicAuthUsername", basicAuthUsername)
  }

  /**
   * The bearer token to use for bearer token authentication
   */
  public fun bearerToken(bearerToken: String) {
    it.property("bearerToken", bearerToken)
  }

  /**
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
