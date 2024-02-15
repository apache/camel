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

public fun UriDsl.cometd(i: CometdUriDsl.() -> Unit) {
  CometdUriDsl(this).apply(i)
}

@CamelDslMarker
public class CometdUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cometd")
  }

  private var host: String = ""

  private var port: String = ""

  private var channelName: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$channelName")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$channelName")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$channelName")
  }

  public fun channelName(channelName: String) {
    this.channelName = channelName
    it.url("$host:$port/$channelName")
  }

  public fun allowedOrigins(allowedOrigins: String) {
    it.property("allowedOrigins", allowedOrigins)
  }

  public fun baseResource(baseResource: String) {
    it.property("baseResource", baseResource)
  }

  public fun crossOriginFilterOn(crossOriginFilterOn: String) {
    it.property("crossOriginFilterOn", crossOriginFilterOn)
  }

  public fun crossOriginFilterOn(crossOriginFilterOn: Boolean) {
    it.property("crossOriginFilterOn", crossOriginFilterOn.toString())
  }

  public fun filterPath(filterPath: String) {
    it.property("filterPath", filterPath)
  }

  public fun interval(interval: String) {
    it.property("interval", interval)
  }

  public fun interval(interval: Int) {
    it.property("interval", interval.toString())
  }

  public fun jsonCommented(jsonCommented: String) {
    it.property("jsonCommented", jsonCommented)
  }

  public fun jsonCommented(jsonCommented: Boolean) {
    it.property("jsonCommented", jsonCommented.toString())
  }

  public fun logLevel(logLevel: String) {
    it.property("logLevel", logLevel)
  }

  public fun logLevel(logLevel: Int) {
    it.property("logLevel", logLevel.toString())
  }

  public fun maxInterval(maxInterval: String) {
    it.property("maxInterval", maxInterval)
  }

  public fun maxInterval(maxInterval: Int) {
    it.property("maxInterval", maxInterval.toString())
  }

  public fun multiFrameInterval(multiFrameInterval: String) {
    it.property("multiFrameInterval", multiFrameInterval)
  }

  public fun multiFrameInterval(multiFrameInterval: Int) {
    it.property("multiFrameInterval", multiFrameInterval.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun sessionHeadersEnabled(sessionHeadersEnabled: String) {
    it.property("sessionHeadersEnabled", sessionHeadersEnabled)
  }

  public fun sessionHeadersEnabled(sessionHeadersEnabled: Boolean) {
    it.property("sessionHeadersEnabled", sessionHeadersEnabled.toString())
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

  public fun disconnectLocalSession(disconnectLocalSession: String) {
    it.property("disconnectLocalSession", disconnectLocalSession)
  }

  public fun disconnectLocalSession(disconnectLocalSession: Boolean) {
    it.property("disconnectLocalSession", disconnectLocalSession.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
