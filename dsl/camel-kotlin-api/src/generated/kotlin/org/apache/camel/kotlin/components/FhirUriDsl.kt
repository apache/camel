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

public fun UriDsl.fhir(i: FhirUriDsl.() -> Unit) {
  FhirUriDsl(this).apply(i)
}

@CamelDslMarker
public class FhirUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("fhir")
  }

  private var apiName: String = ""

  private var methodName: String = ""

  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun fhirVersion(fhirVersion: String) {
    it.property("fhirVersion", fhirVersion)
  }

  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  public fun log(log: String) {
    it.property("log", log)
  }

  public fun log(log: Boolean) {
    it.property("log", log.toString())
  }

  public fun prettyPrint(prettyPrint: String) {
    it.property("prettyPrint", prettyPrint)
  }

  public fun prettyPrint(prettyPrint: Boolean) {
    it.property("prettyPrint", prettyPrint.toString())
  }

  public fun serverUrl(serverUrl: String) {
    it.property("serverUrl", serverUrl)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun client(client: String) {
    it.property("client", client)
  }

  public fun clientFactory(clientFactory: String) {
    it.property("clientFactory", clientFactory)
  }

  public fun compress(compress: String) {
    it.property("compress", compress)
  }

  public fun compress(compress: Boolean) {
    it.property("compress", compress.toString())
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun deferModelScanning(deferModelScanning: String) {
    it.property("deferModelScanning", deferModelScanning)
  }

  public fun deferModelScanning(deferModelScanning: Boolean) {
    it.property("deferModelScanning", deferModelScanning.toString())
  }

  public fun fhirContext(fhirContext: String) {
    it.property("fhirContext", fhirContext)
  }

  public fun forceConformanceCheck(forceConformanceCheck: String) {
    it.property("forceConformanceCheck", forceConformanceCheck)
  }

  public fun forceConformanceCheck(forceConformanceCheck: Boolean) {
    it.property("forceConformanceCheck", forceConformanceCheck.toString())
  }

  public fun sessionCookie(sessionCookie: String) {
    it.property("sessionCookie", sessionCookie)
  }

  public fun socketTimeout(socketTimeout: String) {
    it.property("socketTimeout", socketTimeout)
  }

  public fun socketTimeout(socketTimeout: Int) {
    it.property("socketTimeout", socketTimeout.toString())
  }

  public fun summary(summary: String) {
    it.property("summary", summary)
  }

  public fun validationMode(validationMode: String) {
    it.property("validationMode", validationMode)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPassword(proxyPassword: String) {
    it.property("proxyPassword", proxyPassword)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun proxyUser(proxyUser: String) {
    it.property("proxyUser", proxyUser)
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

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
