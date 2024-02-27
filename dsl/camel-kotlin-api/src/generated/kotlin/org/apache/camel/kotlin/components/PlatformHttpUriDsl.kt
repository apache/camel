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

  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  public fun cookieDomain(cookieDomain: String) {
    it.property("cookieDomain", cookieDomain)
  }

  public fun cookieHttpOnly(cookieHttpOnly: String) {
    it.property("cookieHttpOnly", cookieHttpOnly)
  }

  public fun cookieHttpOnly(cookieHttpOnly: Boolean) {
    it.property("cookieHttpOnly", cookieHttpOnly.toString())
  }

  public fun cookieMaxAge(cookieMaxAge: String) {
    it.property("cookieMaxAge", cookieMaxAge)
  }

  public fun cookieMaxAge(cookieMaxAge: Int) {
    it.property("cookieMaxAge", cookieMaxAge.toString())
  }

  public fun cookiePath(cookiePath: String) {
    it.property("cookiePath", cookiePath)
  }

  public fun cookieSameSite(cookieSameSite: String) {
    it.property("cookieSameSite", cookieSameSite)
  }

  public fun cookieSecure(cookieSecure: String) {
    it.property("cookieSecure", cookieSecure)
  }

  public fun cookieSecure(cookieSecure: Boolean) {
    it.property("cookieSecure", cookieSecure.toString())
  }

  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  public fun matchOnUriPrefix(matchOnUriPrefix: String) {
    it.property("matchOnUriPrefix", matchOnUriPrefix)
  }

  public fun matchOnUriPrefix(matchOnUriPrefix: Boolean) {
    it.property("matchOnUriPrefix", matchOnUriPrefix.toString())
  }

  public fun muteException(muteException: String) {
    it.property("muteException", muteException)
  }

  public fun muteException(muteException: Boolean) {
    it.property("muteException", muteException.toString())
  }

  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  public fun useCookieHandler(useCookieHandler: String) {
    it.property("useCookieHandler", useCookieHandler)
  }

  public fun useCookieHandler(useCookieHandler: Boolean) {
    it.property("useCookieHandler", useCookieHandler.toString())
  }

  public fun useStreaming(useStreaming: String) {
    it.property("useStreaming", useStreaming)
  }

  public fun useStreaming(useStreaming: Boolean) {
    it.property("useStreaming", useStreaming.toString())
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

  public fun fileNameExtWhitelist(fileNameExtWhitelist: String) {
    it.property("fileNameExtWhitelist", fileNameExtWhitelist)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun platformHttpEngine(platformHttpEngine: String) {
    it.property("platformHttpEngine", platformHttpEngine)
  }
}
