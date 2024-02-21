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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.jmx(i: JmxUriDsl.() -> Unit) {
  JmxUriDsl(this).apply(i)
}

@CamelDslMarker
public class JmxUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jmx")
  }

  private var serverURL: String = ""

  public fun serverURL(serverURL: String) {
    this.serverURL = serverURL
    it.url("$serverURL")
  }

  public fun format(format: String) {
    it.property("format", format)
  }

  public fun granularityPeriod(granularityPeriod: String) {
    it.property("granularityPeriod", granularityPeriod)
  }

  public fun monitorType(monitorType: String) {
    it.property("monitorType", monitorType)
  }

  public fun objectDomain(objectDomain: String) {
    it.property("objectDomain", objectDomain)
  }

  public fun objectName(objectName: String) {
    it.property("objectName", objectName)
  }

  public fun observedAttribute(observedAttribute: String) {
    it.property("observedAttribute", observedAttribute)
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

  public fun executorService(executorService: String) {
    it.property("executorService", executorService)
  }

  public fun handback(handback: String) {
    it.property("handback", handback)
  }

  public fun notificationFilter(notificationFilter: String) {
    it.property("notificationFilter", notificationFilter)
  }

  public fun objectProperties(objectProperties: String) {
    it.property("objectProperties", objectProperties)
  }

  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  public fun reconnectDelay(reconnectDelay: Int) {
    it.property("reconnectDelay", reconnectDelay.toString())
  }

  public fun reconnectOnConnectionFailure(reconnectOnConnectionFailure: String) {
    it.property("reconnectOnConnectionFailure", reconnectOnConnectionFailure)
  }

  public fun reconnectOnConnectionFailure(reconnectOnConnectionFailure: Boolean) {
    it.property("reconnectOnConnectionFailure", reconnectOnConnectionFailure.toString())
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  public fun initThreshold(initThreshold: String) {
    it.property("initThreshold", initThreshold)
  }

  public fun initThreshold(initThreshold: Int) {
    it.property("initThreshold", initThreshold.toString())
  }

  public fun modulus(modulus: String) {
    it.property("modulus", modulus)
  }

  public fun modulus(modulus: Int) {
    it.property("modulus", modulus.toString())
  }

  public fun offset(offset: String) {
    it.property("offset", offset)
  }

  public fun offset(offset: Int) {
    it.property("offset", offset.toString())
  }

  public fun differenceMode(differenceMode: String) {
    it.property("differenceMode", differenceMode)
  }

  public fun differenceMode(differenceMode: Boolean) {
    it.property("differenceMode", differenceMode.toString())
  }

  public fun notifyHigh(notifyHigh: String) {
    it.property("notifyHigh", notifyHigh)
  }

  public fun notifyHigh(notifyHigh: Boolean) {
    it.property("notifyHigh", notifyHigh.toString())
  }

  public fun notifyLow(notifyLow: String) {
    it.property("notifyLow", notifyLow)
  }

  public fun notifyLow(notifyLow: Boolean) {
    it.property("notifyLow", notifyLow.toString())
  }

  public fun thresholdHigh(thresholdHigh: String) {
    it.property("thresholdHigh", thresholdHigh)
  }

  public fun thresholdHigh(thresholdHigh: Double) {
    it.property("thresholdHigh", thresholdHigh.toString())
  }

  public fun thresholdLow(thresholdLow: String) {
    it.property("thresholdLow", thresholdLow)
  }

  public fun thresholdLow(thresholdLow: Double) {
    it.property("thresholdLow", thresholdLow.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun user(user: String) {
    it.property("user", user)
  }

  public fun notifyDiffer(notifyDiffer: String) {
    it.property("notifyDiffer", notifyDiffer)
  }

  public fun notifyDiffer(notifyDiffer: Boolean) {
    it.property("notifyDiffer", notifyDiffer.toString())
  }

  public fun notifyMatch(notifyMatch: String) {
    it.property("notifyMatch", notifyMatch)
  }

  public fun notifyMatch(notifyMatch: Boolean) {
    it.property("notifyMatch", notifyMatch.toString())
  }

  public fun stringToCompare(stringToCompare: String) {
    it.property("stringToCompare", stringToCompare)
  }
}
