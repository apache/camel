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
 * Expose HTTP endpoints using the HTTP server available in the current platform.
 */
public fun UriDsl.`platform-http`(i: PlatformHttpUriDsl.() -> Unit) {
  PlatformHttpUriDsl(this).apply(i)
}

@CamelDslMarker
public class PlatformHttpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("platform-http")
  }

  private var path: String = ""

  /**
   * The path under which this endpoint serves the HTTP requests, for proxy use 'proxy'
   */
  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  /**
   * The content type this endpoint accepts as an input, such as application/xml or
   * application/json. null or &#42;/&#42; mean no restriction.
   */
  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  /**
   * Sets which server can receive cookies.
   */
  public fun cookieDomain(cookieDomain: String) {
    it.property("cookieDomain", cookieDomain)
  }

  /**
   * Sets whether to prevent client side scripts from accessing created cookies.
   */
  public fun cookieHttpOnly(cookieHttpOnly: String) {
    it.property("cookieHttpOnly", cookieHttpOnly)
  }

  /**
   * Sets whether to prevent client side scripts from accessing created cookies.
   */
  public fun cookieHttpOnly(cookieHttpOnly: Boolean) {
    it.property("cookieHttpOnly", cookieHttpOnly.toString())
  }

  /**
   * Sets the maximum cookie age in seconds.
   */
  public fun cookieMaxAge(cookieMaxAge: String) {
    it.property("cookieMaxAge", cookieMaxAge)
  }

  /**
   * Sets the maximum cookie age in seconds.
   */
  public fun cookieMaxAge(cookieMaxAge: Int) {
    it.property("cookieMaxAge", cookieMaxAge.toString())
  }

  /**
   * Sets the URL path that must exist in the requested URL in order to send the Cookie.
   */
  public fun cookiePath(cookiePath: String) {
    it.property("cookiePath", cookiePath)
  }

  /**
   * Sets whether to prevent the browser from sending cookies along with cross-site requests.
   */
  public fun cookieSameSite(cookieSameSite: String) {
    it.property("cookieSameSite", cookieSameSite)
  }

  /**
   * Sets whether the cookie is only sent to the server with an encrypted request over HTTPS.
   */
  public fun cookieSecure(cookieSecure: String) {
    it.property("cookieSecure", cookieSecure)
  }

  /**
   * Sets whether the cookie is only sent to the server with an encrypted request over HTTPS.
   */
  public fun cookieSecure(cookieSecure: Boolean) {
    it.property("cookieSecure", cookieSecure.toString())
  }

  /**
   * When Camel is complete processing the message, and the HTTP server is writing response. This
   * option controls whether Camel should catch any failure during writing response and store this on
   * the Exchange, which allows onCompletion/UnitOfWork to regard the Exchange as failed and have
   * access to the caused exception from the HTTP server.
   */
  public fun handleWriteResponseError(handleWriteResponseError: String) {
    it.property("handleWriteResponseError", handleWriteResponseError)
  }

  /**
   * When Camel is complete processing the message, and the HTTP server is writing response. This
   * option controls whether Camel should catch any failure during writing response and store this on
   * the Exchange, which allows onCompletion/UnitOfWork to regard the Exchange as failed and have
   * access to the caused exception from the HTTP server.
   */
  public fun handleWriteResponseError(handleWriteResponseError: Boolean) {
    it.property("handleWriteResponseError", handleWriteResponseError.toString())
  }

  /**
   * A comma separated list of HTTP methods to serve, e.g. GET,POST . If no methods are specified,
   * all methods will be served.
   */
  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: String) {
    it.property("matchOnUriPrefix", matchOnUriPrefix)
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: Boolean) {
    it.property("matchOnUriPrefix", matchOnUriPrefix.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: String) {
    it.property("muteException", muteException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: Boolean) {
    it.property("muteException", muteException.toString())
  }

  /**
   * The content type this endpoint produces, such as application/xml or application/json.
   */
  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  /**
   * Whether to include HTTP request headers (Accept, User-Agent, etc.) into HTTP response produced
   * by this endpoint.
   */
  public fun returnHttpRequestHeaders(returnHttpRequestHeaders: String) {
    it.property("returnHttpRequestHeaders", returnHttpRequestHeaders)
  }

  /**
   * Whether to include HTTP request headers (Accept, User-Agent, etc.) into HTTP response produced
   * by this endpoint.
   */
  public fun returnHttpRequestHeaders(returnHttpRequestHeaders: Boolean) {
    it.property("returnHttpRequestHeaders", returnHttpRequestHeaders.toString())
  }

  /**
   * Whether to enable the Cookie Handler that allows Cookie addition, expiry, and retrieval
   * (currently only supported by camel-platform-http-vertx)
   */
  public fun useCookieHandler(useCookieHandler: String) {
    it.property("useCookieHandler", useCookieHandler)
  }

  /**
   * Whether to enable the Cookie Handler that allows Cookie addition, expiry, and retrieval
   * (currently only supported by camel-platform-http-vertx)
   */
  public fun useCookieHandler(useCookieHandler: Boolean) {
    it.property("useCookieHandler", useCookieHandler.toString())
  }

  /**
   * Whether to use streaming for large requests and responses (currently only supported by
   * camel-platform-http-vertx)
   */
  public fun useStreaming(useStreaming: String) {
    it.property("useStreaming", useStreaming)
  }

  /**
   * Whether to use streaming for large requests and responses (currently only supported by
   * camel-platform-http-vertx)
   */
  public fun useStreaming(useStreaming: Boolean) {
    it.property("useStreaming", useStreaming.toString())
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * A comma or whitespace separated list of file extensions. Uploads having these extensions will
   * be stored locally. Null value or asterisk () will allow all files.
   */
  public fun fileNameExtWhitelist(fileNameExtWhitelist: String) {
    it.property("fileNameExtWhitelist", fileNameExtWhitelist)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter headers to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * An HTTP Server engine implementation to serve the requests of this endpoint.
   */
  public fun platformHttpEngine(platformHttpEngine: String) {
    it.property("platformHttpEngine", platformHttpEngine)
  }
}
