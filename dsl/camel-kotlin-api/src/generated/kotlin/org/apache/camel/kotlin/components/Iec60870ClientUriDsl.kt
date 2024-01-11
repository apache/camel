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

public fun UriDsl.`iec60870-client`(i: Iec60870ClientUriDsl.() -> Unit) {
  Iec60870ClientUriDsl(this).apply(i)
}

@CamelDslMarker
public class Iec60870ClientUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("iec60870-client")
  }

  private var uriPath: String = ""

  public fun uriPath(uriPath: String) {
    this.uriPath = uriPath
    it.url("$uriPath")
  }

  public fun dataModuleOptions(dataModuleOptions: String) {
    it.property("dataModuleOptions", dataModuleOptions)
  }

  public fun protocolOptions(protocolOptions: String) {
    it.property("protocolOptions", protocolOptions)
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun acknowledgeWindow(acknowledgeWindow: String) {
    it.property("acknowledgeWindow", acknowledgeWindow)
  }

  public fun acknowledgeWindow(acknowledgeWindow: Int) {
    it.property("acknowledgeWindow", acknowledgeWindow.toString())
  }

  public fun adsuAddressType(adsuAddressType: String) {
    it.property("adsuAddressType", adsuAddressType)
  }

  public fun causeOfTransmissionType(causeOfTransmissionType: String) {
    it.property("causeOfTransmissionType", causeOfTransmissionType)
  }

  public fun informationObjectAddressType(informationObjectAddressType: String) {
    it.property("informationObjectAddressType", informationObjectAddressType)
  }

  public fun maxUnacknowledged(maxUnacknowledged: String) {
    it.property("maxUnacknowledged", maxUnacknowledged)
  }

  public fun maxUnacknowledged(maxUnacknowledged: Int) {
    it.property("maxUnacknowledged", maxUnacknowledged.toString())
  }

  public fun timeout1(timeout1: String) {
    it.property("timeout1", timeout1)
  }

  public fun timeout1(timeout1: Int) {
    it.property("timeout1", timeout1.toString())
  }

  public fun timeout2(timeout2: String) {
    it.property("timeout2", timeout2)
  }

  public fun timeout2(timeout2: Int) {
    it.property("timeout2", timeout2.toString())
  }

  public fun timeout3(timeout3: String) {
    it.property("timeout3", timeout3)
  }

  public fun timeout3(timeout3: Int) {
    it.property("timeout3", timeout3.toString())
  }

  public fun causeSourceAddress(causeSourceAddress: String) {
    it.property("causeSourceAddress", causeSourceAddress)
  }

  public fun causeSourceAddress(causeSourceAddress: Int) {
    it.property("causeSourceAddress", causeSourceAddress.toString())
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun ignoreBackgroundScan(ignoreBackgroundScan: String) {
    it.property("ignoreBackgroundScan", ignoreBackgroundScan)
  }

  public fun ignoreBackgroundScan(ignoreBackgroundScan: Boolean) {
    it.property("ignoreBackgroundScan", ignoreBackgroundScan.toString())
  }

  public fun ignoreDaylightSavingTime(ignoreDaylightSavingTime: String) {
    it.property("ignoreDaylightSavingTime", ignoreDaylightSavingTime)
  }

  public fun ignoreDaylightSavingTime(ignoreDaylightSavingTime: Boolean) {
    it.property("ignoreDaylightSavingTime", ignoreDaylightSavingTime.toString())
  }

  public fun timeZone(timeZone: String) {
    it.property("timeZone", timeZone)
  }

  public fun connectionId(connectionId: String) {
    it.property("connectionId", connectionId)
  }
}
