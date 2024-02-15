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

public fun UriDsl.`google-sheets-stream`(i: GoogleSheetsStreamUriDsl.() -> Unit) {
  GoogleSheetsStreamUriDsl(this).apply(i)
}

@CamelDslMarker
public class GoogleSheetsStreamUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("google-sheets-stream")
  }

  private var spreadsheetId: String = ""

  public fun spreadsheetId(spreadsheetId: String) {
    this.spreadsheetId = spreadsheetId
    it.url("$spreadsheetId")
  }

  public fun applicationName(applicationName: String) {
    it.property("applicationName", applicationName)
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun `delegate`(`delegate`: String) {
    it.property("delegate", delegate)
  }

  public fun includeGridData(includeGridData: String) {
    it.property("includeGridData", includeGridData)
  }

  public fun includeGridData(includeGridData: Boolean) {
    it.property("includeGridData", includeGridData.toString())
  }

  public fun majorDimension(majorDimension: String) {
    it.property("majorDimension", majorDimension)
  }

  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  public fun range(range: String) {
    it.property("range", range)
  }

  public fun scopes(scopes: String) {
    it.property("scopes", scopes)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun splitResults(splitResults: String) {
    it.property("splitResults", splitResults)
  }

  public fun splitResults(splitResults: Boolean) {
    it.property("splitResults", splitResults.toString())
  }

  public fun valueRenderOption(valueRenderOption: String) {
    it.property("valueRenderOption", valueRenderOption)
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

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  public fun refreshToken(refreshToken: String) {
    it.property("refreshToken", refreshToken)
  }

  public fun serviceAccountKey(serviceAccountKey: String) {
    it.property("serviceAccountKey", serviceAccountKey)
  }
}
