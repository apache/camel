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

/**
 * Exchanges messages with an IBM i system using data queues, message queues, or program call. IBM i
 * is the replacement for AS/400 and iSeries servers.
 */
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

  /**
   * Returns the ID of the IBM i user.
   */
  public fun userID(userID: String) {
    this.userID = userID
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  /**
   * Returns the password of the IBM i user.
   */
  public fun password(password: String) {
    this.password = password
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  /**
   * Returns the name of the IBM i system.
   */
  public fun systemName(systemName: String) {
    this.systemName = systemName
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  /**
   * Returns the fully qualified integrated file system path name of the target object of this
   * endpoint.
   */
  public fun objectPath(objectPath: String) {
    this.objectPath = objectPath
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  /**
   * Whether to work with data queues or remote program call
   */
  public fun type(type: String) {
    this.type = type
    it.url("$userID:$password@$systemName/QSYS.LIB/$objectPath.$type")
  }

  /**
   * Sets the CCSID to use for the connection with the IBM i system.
   */
  public fun ccsid(ccsid: String) {
    it.property("ccsid", ccsid)
  }

  /**
   * Sets the CCSID to use for the connection with the IBM i system.
   */
  public fun ccsid(ccsid: Int) {
    it.property("ccsid", ccsid.toString())
  }

  /**
   * Sets the data format for sending messages.
   */
  public fun format(format: String) {
    it.property("format", format)
  }

  /**
   * Sets whether IBM i prompting is enabled in the environment running Camel.
   */
  public fun guiAvailable(guiAvailable: String) {
    it.property("guiAvailable", guiAvailable)
  }

  /**
   * Sets whether IBM i prompting is enabled in the environment running Camel.
   */
  public fun guiAvailable(guiAvailable: Boolean) {
    it.property("guiAvailable", guiAvailable.toString())
  }

  /**
   * Whether to use keyed or non-keyed data queues.
   */
  public fun keyed(keyed: String) {
    it.property("keyed", keyed)
  }

  /**
   * Whether to use keyed or non-keyed data queues.
   */
  public fun keyed(keyed: Boolean) {
    it.property("keyed", keyed.toString())
  }

  /**
   * Search key for keyed data queues.
   */
  public fun searchKey(searchKey: String) {
    it.property("searchKey", searchKey)
  }

  /**
   * Action to be taken on messages when read from a message queue. Messages can be marked as old
   * (OLD), removed from the queue (REMOVE), or neither (SAME).
   */
  public fun messageAction(messageAction: String) {
    it.property("messageAction", messageAction)
  }

  /**
   * Timeout in millis the consumer will wait while trying to read a new message of the data queue.
   */
  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  /**
   * Timeout in millis the consumer will wait while trying to read a new message of the data queue.
   */
  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }

  /**
   * Search type such as EQ for equal etc.
   */
  public fun searchType(searchType: String) {
    it.property("searchType", searchType)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  /**
   * If true, the consumer endpoint will set the Jt400Constants.MESSAGE_REPLYTO_KEY header of the
   * camel message for any IBM i inquiry messages received. If that message is then routed to a
   * producer endpoint, the action will not be processed as sending a message to the queue, but rather
   * a reply to the specific inquiry message.
   */
  public fun sendingReply(sendingReply: String) {
    it.property("sendingReply", sendingReply)
  }

  /**
   * If true, the consumer endpoint will set the Jt400Constants.MESSAGE_REPLYTO_KEY header of the
   * camel message for any IBM i inquiry messages received. If that message is then routed to a
   * producer endpoint, the action will not be processed as sending a message to the queue, but rather
   * a reply to the specific inquiry message.
   */
  public fun sendingReply(sendingReply: Boolean) {
    it.property("sendingReply", sendingReply.toString())
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
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  /**
   * Specifies which fields (program parameters) are output parameters.
   */
  public fun outputFieldsIdxArray(outputFieldsIdxArray: String) {
    it.property("outputFieldsIdxArray", outputFieldsIdxArray)
  }

  /**
   * Specifies the fields (program parameters) length as in the IBM i program definition.
   */
  public fun outputFieldsLengthArray(outputFieldsLengthArray: String) {
    it.property("outputFieldsLengthArray", outputFieldsLengthArray)
  }

  /**
   * Procedure name from a service program to call
   */
  public fun procedureName(procedureName: String) {
    it.property("procedureName", procedureName)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  /**
   * Allows for configuring a custom/shared thread pool to use for the consumer. By default each
   * consumer has its own single threaded thread pool.
   */
  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  /**
   * To use a cron scheduler from either camel-spring or camel-quartz component. Use value spring or
   * quartz for built in scheduler
   */
  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  /**
   * To configure additional properties when using a custom scheduler or any of the Quartz, Spring
   * based scheduler.
   */
  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  /**
   * Time unit for initialDelay and delay options.
   */
  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  /**
   * Whether connections to IBM i are secured with SSL.
   */
  public fun secured(secured: String) {
    it.property("secured", secured)
  }

  /**
   * Whether connections to IBM i are secured with SSL.
   */
  public fun secured(secured: Boolean) {
    it.property("secured", secured.toString())
  }
}
