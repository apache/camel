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

public fun UriDsl.jt400(i: Jt400UriDsl.() -> Unit) {
  Jt400UriDsl(this).apply(i)
}

@CamelDslMarker
public class Jt400UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jt400")
  }

  private var userID: String = ""

  private var password: String = ""

  private var systemName: String = ""

  private var objectPath: String = ""

  private var type: String = ""

  public fun userID(userID: String) {
    this.userID = userID
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  public fun password(password: String) {
    this.password = password
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  public fun systemName(systemName: String) {
    this.systemName = systemName
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  public fun objectPath(objectPath: String) {
    this.objectPath = objectPath
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  public fun type(type: String) {
    this.type = type
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  public fun ccsid(ccsid: String) {
    it.property("ccsid", ccsid)
  }

  public fun ccsid(ccsid: Int) {
    it.property("ccsid", ccsid.toString())
  }

  public fun format(format: String) {
    it.property("format", format)
  }

  public fun guiAvailable(guiAvailable: String) {
    it.property("guiAvailable", guiAvailable)
  }

  public fun guiAvailable(guiAvailable: Boolean) {
    it.property("guiAvailable", guiAvailable.toString())
  }

  public fun keyed(keyed: String) {
    it.property("keyed", keyed)
  }

  public fun keyed(keyed: Boolean) {
    it.property("keyed", keyed.toString())
  }

  public fun searchKey(searchKey: String) {
    it.property("searchKey", searchKey)
  }

  public fun messageAction(messageAction: String) {
    it.property("messageAction", messageAction)
  }

  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }

  public fun searchType(searchType: String) {
    it.property("searchType", searchType)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun sendingReply(sendingReply: String) {
    it.property("sendingReply", sendingReply)
  }

  public fun sendingReply(sendingReply: Boolean) {
    it.property("sendingReply", sendingReply.toString())
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

  public fun outputFieldsIdxArray(outputFieldsIdxArray: String) {
    it.property("outputFieldsIdxArray", outputFieldsIdxArray)
  }

  public fun outputFieldsLengthArray(outputFieldsLengthArray: String) {
    it.property("outputFieldsLengthArray", outputFieldsLengthArray)
  }

  public fun procedureName(procedureName: String) {
    it.property("procedureName", procedureName)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
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

  public fun secured(secured: String) {
    it.property("secured", secured)
  }

  public fun secured(secured: Boolean) {
    it.property("secured", secured.toString())
  }
}
