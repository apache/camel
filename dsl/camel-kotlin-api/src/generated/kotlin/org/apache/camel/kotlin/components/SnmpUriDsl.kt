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

public fun UriDsl.snmp(i: SnmpUriDsl.() -> Unit) {
  SnmpUriDsl(this).apply(i)
}

@CamelDslMarker
public class SnmpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("snmp")
  }

  private var host: String = ""

  private var port: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  public fun oids(oids: String) {
    it.property("oids", oids)
  }

  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  public fun retries(retries: String) {
    it.property("retries", retries)
  }

  public fun retries(retries: Int) {
    it.property("retries", retries.toString())
  }

  public fun snmpCommunity(snmpCommunity: String) {
    it.property("snmpCommunity", snmpCommunity)
  }

  public fun snmpContextEngineId(snmpContextEngineId: String) {
    it.property("snmpContextEngineId", snmpContextEngineId)
  }

  public fun snmpContextName(snmpContextName: String) {
    it.property("snmpContextName", snmpContextName)
  }

  public fun snmpVersion(snmpVersion: String) {
    it.property("snmpVersion", snmpVersion)
  }

  public fun snmpVersion(snmpVersion: Int) {
    it.property("snmpVersion", snmpVersion.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun type(type: String) {
    it.property("type", type)
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun treeList(treeList: String) {
    it.property("treeList", treeList)
  }

  public fun treeList(treeList: Boolean) {
    it.property("treeList", treeList.toString())
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

  public fun authenticationPassphrase(authenticationPassphrase: String) {
    it.property("authenticationPassphrase", authenticationPassphrase)
  }

  public fun authenticationProtocol(authenticationProtocol: String) {
    it.property("authenticationProtocol", authenticationProtocol)
  }

  public fun privacyPassphrase(privacyPassphrase: String) {
    it.property("privacyPassphrase", privacyPassphrase)
  }

  public fun privacyProtocol(privacyProtocol: String) {
    it.property("privacyProtocol", privacyProtocol)
  }

  public fun securityLevel(securityLevel: String) {
    it.property("securityLevel", securityLevel)
  }

  public fun securityLevel(securityLevel: Int) {
    it.property("securityLevel", securityLevel.toString())
  }

  public fun securityName(securityName: String) {
    it.property("securityName", securityName)
  }
}
