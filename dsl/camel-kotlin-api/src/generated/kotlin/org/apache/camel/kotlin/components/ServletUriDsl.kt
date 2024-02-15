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

public fun UriDsl.servlet(i: ServletUriDsl.() -> Unit) {
  ServletUriDsl(this).apply(i)
}

@CamelDslMarker
public class ServletUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("servlet")
  }

  private var contextPath: String = ""

  public fun contextPath(contextPath: String) {
    this.contextPath = contextPath
    it.url("$contextPath")
  }

  public fun disableStreamCache(disableStreamCache: String) {
    it.property("disableStreamCache", disableStreamCache)
  }

  public fun disableStreamCache(disableStreamCache: Boolean) {
    it.property("disableStreamCache", disableStreamCache.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun httpBinding(httpBinding: String) {
    it.property("httpBinding", httpBinding)
  }

  public fun chunked(chunked: String) {
    it.property("chunked", chunked)
  }

  public fun chunked(chunked: Boolean) {
    it.property("chunked", chunked.toString())
  }

  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  public fun async(async: String) {
    it.property("async", async)
  }

  public fun async(async: Boolean) {
    it.property("async", async.toString())
  }

  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  public fun logException(logException: String) {
    it.property("logException", logException)
  }

  public fun logException(logException: Boolean) {
    it.property("logException", logException.toString())
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

  public fun responseBufferSize(responseBufferSize: String) {
    it.property("responseBufferSize", responseBufferSize)
  }

  public fun responseBufferSize(responseBufferSize: Int) {
    it.property("responseBufferSize", responseBufferSize.toString())
  }

  public fun servletName(servletName: String) {
    it.property("servletName", servletName)
  }

  public fun attachmentMultipartBinding(attachmentMultipartBinding: String) {
    it.property("attachmentMultipartBinding", attachmentMultipartBinding)
  }

  public fun attachmentMultipartBinding(attachmentMultipartBinding: Boolean) {
    it.property("attachmentMultipartBinding", attachmentMultipartBinding.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun eagerCheckContentAvailable(eagerCheckContentAvailable: String) {
    it.property("eagerCheckContentAvailable", eagerCheckContentAvailable)
  }

  public fun eagerCheckContentAvailable(eagerCheckContentAvailable: Boolean) {
    it.property("eagerCheckContentAvailable", eagerCheckContentAvailable.toString())
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

  public fun mapHttpMessageBody(mapHttpMessageBody: String) {
    it.property("mapHttpMessageBody", mapHttpMessageBody)
  }

  public fun mapHttpMessageBody(mapHttpMessageBody: Boolean) {
    it.property("mapHttpMessageBody", mapHttpMessageBody.toString())
  }

  public fun mapHttpMessageFormUrlEncodedBody(mapHttpMessageFormUrlEncodedBody: String) {
    it.property("mapHttpMessageFormUrlEncodedBody", mapHttpMessageFormUrlEncodedBody)
  }

  public fun mapHttpMessageFormUrlEncodedBody(mapHttpMessageFormUrlEncodedBody: Boolean) {
    it.property("mapHttpMessageFormUrlEncodedBody", mapHttpMessageFormUrlEncodedBody.toString())
  }

  public fun mapHttpMessageHeaders(mapHttpMessageHeaders: String) {
    it.property("mapHttpMessageHeaders", mapHttpMessageHeaders)
  }

  public fun mapHttpMessageHeaders(mapHttpMessageHeaders: Boolean) {
    it.property("mapHttpMessageHeaders", mapHttpMessageHeaders.toString())
  }

  public fun optionsEnabled(optionsEnabled: String) {
    it.property("optionsEnabled", optionsEnabled)
  }

  public fun optionsEnabled(optionsEnabled: Boolean) {
    it.property("optionsEnabled", optionsEnabled.toString())
  }

  public fun traceEnabled(traceEnabled: String) {
    it.property("traceEnabled", traceEnabled)
  }

  public fun traceEnabled(traceEnabled: Boolean) {
    it.property("traceEnabled", traceEnabled.toString())
  }
}
