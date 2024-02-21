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

public fun UriDsl.undertow(i: UndertowUriDsl.() -> Unit) {
  UndertowUriDsl(this).apply(i)
}

@CamelDslMarker
public class UndertowUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("undertow")
  }

  private var httpURI: String = ""

  public fun httpURI(httpURI: String) {
    this.httpURI = httpURI
    it.url("$httpURI")
  }

  public fun useStreaming(useStreaming: String) {
    it.property("useStreaming", useStreaming)
  }

  public fun useStreaming(useStreaming: Boolean) {
    it.property("useStreaming", useStreaming.toString())
  }

  public fun accessLog(accessLog: String) {
    it.property("accessLog", accessLog)
  }

  public fun accessLog(accessLog: Boolean) {
    it.property("accessLog", accessLog.toString())
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

  public fun optionsEnabled(optionsEnabled: String) {
    it.property("optionsEnabled", optionsEnabled)
  }

  public fun optionsEnabled(optionsEnabled: Boolean) {
    it.property("optionsEnabled", optionsEnabled.toString())
  }

  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
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

  public fun handlers(handlers: String) {
    it.property("handlers", handlers)
  }

  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  public fun options(options: String) {
    it.property("options", options)
  }

  public fun preserveHostHeader(preserveHostHeader: String) {
    it.property("preserveHostHeader", preserveHostHeader)
  }

  public fun preserveHostHeader(preserveHostHeader: Boolean) {
    it.property("preserveHostHeader", preserveHostHeader.toString())
  }

  public fun reuseAddresses(reuseAddresses: String) {
    it.property("reuseAddresses", reuseAddresses)
  }

  public fun reuseAddresses(reuseAddresses: Boolean) {
    it.property("reuseAddresses", reuseAddresses.toString())
  }

  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun accessLogReceiver(accessLogReceiver: String) {
    it.property("accessLogReceiver", accessLogReceiver)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun undertowHttpBinding(undertowHttpBinding: String) {
    it.property("undertowHttpBinding", undertowHttpBinding)
  }

  public fun allowedRoles(allowedRoles: String) {
    it.property("allowedRoles", allowedRoles)
  }

  public fun securityConfiguration(securityConfiguration: String) {
    it.property("securityConfiguration", securityConfiguration)
  }

  public fun securityProvider(securityProvider: String) {
    it.property("securityProvider", securityProvider)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun fireWebSocketChannelEvents(fireWebSocketChannelEvents: String) {
    it.property("fireWebSocketChannelEvents", fireWebSocketChannelEvents)
  }

  public fun fireWebSocketChannelEvents(fireWebSocketChannelEvents: Boolean) {
    it.property("fireWebSocketChannelEvents", fireWebSocketChannelEvents.toString())
  }

  public fun sendTimeout(sendTimeout: String) {
    it.property("sendTimeout", sendTimeout)
  }

  public fun sendTimeout(sendTimeout: Int) {
    it.property("sendTimeout", sendTimeout.toString())
  }

  public fun sendToAll(sendToAll: String) {
    it.property("sendToAll", sendToAll)
  }

  public fun sendToAll(sendToAll: Boolean) {
    it.property("sendToAll", sendToAll.toString())
  }
}
