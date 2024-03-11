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

/**
 * Schedule sending of messages using the Quartz 2.x scheduler.
 */
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

  /**
   * The quartz group name to use. The combination of group name and trigger name should be unique.
   */
  public fun groupName(groupName: String) {
    this.groupName = groupName
    it.url("$groupName/$triggerName")
  }

  /**
   * The quartz trigger name to use. The combination of group name and trigger name should be
   * unique.
   */
  public fun triggerName(triggerName: String) {
    this.triggerName = triggerName
    it.url("$groupName/$triggerName")
  }

  /**
   * Specifies a cron expression to define when to trigger.
   */
  public fun cron(cron: String) {
    it.property("cron", cron)
  }

  /**
   * If set to true, then the trigger automatically delete when route stop. Else if set to false, it
   * will remain in scheduler. When set to false, it will also mean user may reuse pre-configured
   * trigger with camel Uri. Just ensure the names match. Notice you cannot have both deleteJob and
   * pauseJob set to true.
   */
  public fun deleteJob(deleteJob: String) {
    it.property("deleteJob", deleteJob)
  }

  /**
   * If set to true, then the trigger automatically delete when route stop. Else if set to false, it
   * will remain in scheduler. When set to false, it will also mean user may reuse pre-configured
   * trigger with camel Uri. Just ensure the names match. Notice you cannot have both deleteJob and
   * pauseJob set to true.
   */
  public fun deleteJob(deleteJob: Boolean) {
    it.property("deleteJob", deleteJob.toString())
  }

  /**
   * Whether or not the job should remain stored after it is orphaned (no triggers point to it).
   */
  public fun durableJob(durableJob: String) {
    it.property("durableJob", durableJob)
  }

  /**
   * Whether or not the job should remain stored after it is orphaned (no triggers point to it).
   */
  public fun durableJob(durableJob: Boolean) {
    it.property("durableJob", durableJob.toString())
  }

  /**
   * If set to true, then the trigger automatically pauses when route stop. Else if set to false, it
   * will remain in scheduler. When set to false, it will also mean user may reuse pre-configured
   * trigger with camel Uri. Just ensure the names match. Notice you cannot have both deleteJob and
   * pauseJob set to true.
   */
  public fun pauseJob(pauseJob: String) {
    it.property("pauseJob", pauseJob)
  }

  /**
   * If set to true, then the trigger automatically pauses when route stop. Else if set to false, it
   * will remain in scheduler. When set to false, it will also mean user may reuse pre-configured
   * trigger with camel Uri. Just ensure the names match. Notice you cannot have both deleteJob and
   * pauseJob set to true.
   */
  public fun pauseJob(pauseJob: Boolean) {
    it.property("pauseJob", pauseJob.toString())
  }

  /**
   * Instructs the scheduler whether or not the job should be re-executed if a 'recovery' or
   * 'fail-over' situation is encountered.
   */
  public fun recoverableJob(recoverableJob: String) {
    it.property("recoverableJob", recoverableJob)
  }

  /**
   * Instructs the scheduler whether or not the job should be re-executed if a 'recovery' or
   * 'fail-over' situation is encountered.
   */
  public fun recoverableJob(recoverableJob: Boolean) {
    it.property("recoverableJob", recoverableJob.toString())
  }

  /**
   * Uses a Quartz PersistJobDataAfterExecution and DisallowConcurrentExecution instead of the
   * default job.
   */
  public fun stateful(stateful: String) {
    it.property("stateful", stateful)
  }

  /**
   * Uses a Quartz PersistJobDataAfterExecution and DisallowConcurrentExecution instead of the
   * default job.
   */
  public fun stateful(stateful: Boolean) {
    it.property("stateful", stateful.toString())
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * Specifies a custom calendar to avoid specific range of date
   */
  public fun customCalendar(customCalendar: String) {
    it.property("customCalendar", customCalendar)
  }

  /**
   * Whether to ignore quartz cannot schedule a trigger because the trigger will never fire in the
   * future. This can happen when using a cron trigger that are configured to only run in the past. By
   * default, Quartz will fail to schedule the trigger and therefore fail to start the Camel route. You
   * can set this to true which then logs a WARN and then ignore the problem, meaning that the route
   * will never fire in the future.
   */
  public fun ignoreExpiredNextFireTime(ignoreExpiredNextFireTime: String) {
    it.property("ignoreExpiredNextFireTime", ignoreExpiredNextFireTime)
  }

  /**
   * Whether to ignore quartz cannot schedule a trigger because the trigger will never fire in the
   * future. This can happen when using a cron trigger that are configured to only run in the past. By
   * default, Quartz will fail to schedule the trigger and therefore fail to start the Camel route. You
   * can set this to true which then logs a WARN and then ignore the problem, meaning that the route
   * will never fire in the future.
   */
  public fun ignoreExpiredNextFireTime(ignoreExpiredNextFireTime: Boolean) {
    it.property("ignoreExpiredNextFireTime", ignoreExpiredNextFireTime.toString())
  }

  /**
   * To configure additional options on the job.
   */
  public fun jobParameters(jobParameters: String) {
    it.property("jobParameters", jobParameters)
  }

  /**
   * Whether the job name should be prefixed with endpoint id
   */
  public fun prefixJobNameWithEndpointId(prefixJobNameWithEndpointId: String) {
    it.property("prefixJobNameWithEndpointId", prefixJobNameWithEndpointId)
  }

  /**
   * Whether the job name should be prefixed with endpoint id
   */
  public fun prefixJobNameWithEndpointId(prefixJobNameWithEndpointId: Boolean) {
    it.property("prefixJobNameWithEndpointId", prefixJobNameWithEndpointId.toString())
  }

  /**
   * To configure additional options on the trigger. The parameter timeZone is supported if the cron
   * option is present. Otherwise the parameters repeatInterval and repeatCount are supported. Note:
   * When using repeatInterval values of 1000 or less, the first few events after starting the camel
   * context may be fired more rapidly than expected.
   */
  public fun triggerParameters(triggerParameters: String) {
    it.property("triggerParameters", triggerParameters)
  }

  /**
   * If it is true, JobDataMap uses the CamelContext name directly to reference the CamelContext, if
   * it is false, JobDataMap uses use the CamelContext management name which could be changed during
   * the deploy time.
   */
  public fun usingFixedCamelContextName(usingFixedCamelContextName: String) {
    it.property("usingFixedCamelContextName", usingFixedCamelContextName)
  }

  /**
   * If it is true, JobDataMap uses the CamelContext name directly to reference the CamelContext, if
   * it is false, JobDataMap uses use the CamelContext management name which could be changed during
   * the deploy time.
   */
  public fun usingFixedCamelContextName(usingFixedCamelContextName: Boolean) {
    it.property("usingFixedCamelContextName", usingFixedCamelContextName.toString())
  }

  /**
   * Whether or not the scheduler should be auto started.
   */
  public fun autoStartScheduler(autoStartScheduler: String) {
    it.property("autoStartScheduler", autoStartScheduler)
  }

  /**
   * Whether or not the scheduler should be auto started.
   */
  public fun autoStartScheduler(autoStartScheduler: Boolean) {
    it.property("autoStartScheduler", autoStartScheduler.toString())
  }

  /**
   * In case of scheduler has already started, we want the trigger start slightly after current time
   * to ensure endpoint is fully started before the job kicks in. Negative value shifts trigger start
   * time in the past.
   */
  public fun triggerStartDelay(triggerStartDelay: String) {
    it.property("triggerStartDelay", triggerStartDelay)
  }
}
