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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.quartz(i: QuartzUriDsl.() -> Unit) {
  QuartzUriDsl(this).apply(i)
}

@CamelDslMarker
public class QuartzUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("quartz")
  }

  private var groupName: String = ""

  private var triggerName: String = ""

  public fun groupName(groupName: String) {
    this.groupName = groupName
    it.url("$groupName/$triggerName")
  }

  public fun triggerName(triggerName: String) {
    this.triggerName = triggerName
    it.url("$groupName/$triggerName")
  }

  public fun cron(cron: String) {
    it.property("cron", cron)
  }

  public fun deleteJob(deleteJob: String) {
    it.property("deleteJob", deleteJob)
  }

  public fun deleteJob(deleteJob: Boolean) {
    it.property("deleteJob", deleteJob.toString())
  }

  public fun durableJob(durableJob: String) {
    it.property("durableJob", durableJob)
  }

  public fun durableJob(durableJob: Boolean) {
    it.property("durableJob", durableJob.toString())
  }

  public fun pauseJob(pauseJob: String) {
    it.property("pauseJob", pauseJob)
  }

  public fun pauseJob(pauseJob: Boolean) {
    it.property("pauseJob", pauseJob.toString())
  }

  public fun recoverableJob(recoverableJob: String) {
    it.property("recoverableJob", recoverableJob)
  }

  public fun recoverableJob(recoverableJob: Boolean) {
    it.property("recoverableJob", recoverableJob.toString())
  }

  public fun stateful(stateful: String) {
    it.property("stateful", stateful)
  }

  public fun stateful(stateful: Boolean) {
    it.property("stateful", stateful.toString())
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

  public fun customCalendar(customCalendar: String) {
    it.property("customCalendar", customCalendar)
  }

  public fun ignoreExpiredNextFireTime(ignoreExpiredNextFireTime: String) {
    it.property("ignoreExpiredNextFireTime", ignoreExpiredNextFireTime)
  }

  public fun ignoreExpiredNextFireTime(ignoreExpiredNextFireTime: Boolean) {
    it.property("ignoreExpiredNextFireTime", ignoreExpiredNextFireTime.toString())
  }

  public fun jobParameters(jobParameters: String) {
    it.property("jobParameters", jobParameters)
  }

  public fun prefixJobNameWithEndpointId(prefixJobNameWithEndpointId: String) {
    it.property("prefixJobNameWithEndpointId", prefixJobNameWithEndpointId)
  }

  public fun prefixJobNameWithEndpointId(prefixJobNameWithEndpointId: Boolean) {
    it.property("prefixJobNameWithEndpointId", prefixJobNameWithEndpointId.toString())
  }

  public fun triggerParameters(triggerParameters: String) {
    it.property("triggerParameters", triggerParameters)
  }

  public fun usingFixedCamelContextName(usingFixedCamelContextName: String) {
    it.property("usingFixedCamelContextName", usingFixedCamelContextName)
  }

  public fun usingFixedCamelContextName(usingFixedCamelContextName: Boolean) {
    it.property("usingFixedCamelContextName", usingFixedCamelContextName.toString())
  }

  public fun autoStartScheduler(autoStartScheduler: String) {
    it.property("autoStartScheduler", autoStartScheduler)
  }

  public fun autoStartScheduler(autoStartScheduler: Boolean) {
    it.property("autoStartScheduler", autoStartScheduler.toString())
  }

  public fun triggerStartDelay(triggerStartDelay: String) {
    it.property("triggerStartDelay", triggerStartDelay)
  }
}
