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
 * Query Couchbase Views with a poll strategy and/or perform various operations against Couchbase
 * databases.
 */
public fun UriDsl.couchbase(i: CouchbaseUriDsl.() -> Unit) {
  CouchbaseUriDsl(this).apply(i)
}

@CamelDslMarker
public class CouchbaseUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("couchbase")
  }

  private var protocol: String = ""

  private var hostname: String = ""

  private var port: String = ""

  /**
   * The protocol to use
   */
  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol://$hostname:$port")
  }

  /**
   * The hostname to use
   */
  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$protocol://$hostname:$port")
  }

  /**
   * The port number to use
   */
  public fun port(port: String) {
    this.port = port
    it.url("$protocol://$hostname:$port")
  }

  /**
   * The port number to use
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol://$hostname:$port")
  }

  /**
   * The bucket to use
   */
  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  /**
   * The collection to use
   */
  public fun collection(collection: String) {
    it.property("collection", collection)
  }

  /**
   * The key to use
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * The scope to use
   */
  public fun scope(scope: String) {
    it.property("scope", scope)
  }

  /**
   * Define the consumer Processed strategy to use
   */
  public fun consumerProcessedStrategy(consumerProcessedStrategy: String) {
    it.property("consumerProcessedStrategy", consumerProcessedStrategy)
  }

  /**
   * Define if this operation is descending or not
   */
  public fun descending(descending: String) {
    it.property("descending", descending)
  }

  /**
   * Define if this operation is descending or not
   */
  public fun descending(descending: Boolean) {
    it.property("descending", descending.toString())
  }

  /**
   * The design document name to use
   */
  public fun designDocumentName(designDocumentName: String) {
    it.property("designDocumentName", designDocumentName)
  }

  /**
   * If true consumer will return complete document instead data defined in view
   */
  public fun fullDocument(fullDocument: String) {
    it.property("fullDocument", fullDocument)
  }

  /**
   * If true consumer will return complete document instead data defined in view
   */
  public fun fullDocument(fullDocument: Boolean) {
    it.property("fullDocument", fullDocument.toString())
  }

  /**
   * The output limit to use
   */
  public fun limit(limit: String) {
    it.property("limit", limit)
  }

  /**
   * The output limit to use
   */
  public fun limit(limit: Int) {
    it.property("limit", limit.toString())
  }

  /**
   * Define a range for the end key
   */
  public fun rangeEndKey(rangeEndKey: String) {
    it.property("rangeEndKey", rangeEndKey)
  }

  /**
   * Define a range for the start key
   */
  public fun rangeStartKey(rangeStartKey: String) {
    it.property("rangeStartKey", rangeStartKey)
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
   * Define the skip to use
   */
  public fun skip(skip: String) {
    it.property("skip", skip)
  }

  /**
   * Define the skip to use
   */
  public fun skip(skip: Int) {
    it.property("skip", skip.toString())
  }

  /**
   * The view name to use
   */
  public fun viewName(viewName: String) {
    it.property("viewName", viewName)
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
   * Define if we want an autostart Id when we are doing an insert operation
   */
  public fun autoStartIdForInserts(autoStartIdForInserts: String) {
    it.property("autoStartIdForInserts", autoStartIdForInserts)
  }

  /**
   * Define if we want an autostart Id when we are doing an insert operation
   */
  public fun autoStartIdForInserts(autoStartIdForInserts: Boolean) {
    it.property("autoStartIdForInserts", autoStartIdForInserts.toString())
  }

  /**
   * The operation to do
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Where to persist the data
   */
  public fun persistTo(persistTo: String) {
    it.property("persistTo", persistTo)
  }

  /**
   * Where to persist the data
   */
  public fun persistTo(persistTo: Int) {
    it.property("persistTo", persistTo.toString())
  }

  /**
   * Define the number of retry attempts
   */
  public fun producerRetryAttempts(producerRetryAttempts: String) {
    it.property("producerRetryAttempts", producerRetryAttempts)
  }

  /**
   * Define the number of retry attempts
   */
  public fun producerRetryAttempts(producerRetryAttempts: Int) {
    it.property("producerRetryAttempts", producerRetryAttempts.toString())
  }

  /**
   * Define the retry pause between different attempts
   */
  public fun producerRetryPause(producerRetryPause: String) {
    it.property("producerRetryPause", producerRetryPause)
  }

  /**
   * Define the retry pause between different attempts
   */
  public fun producerRetryPause(producerRetryPause: Int) {
    it.property("producerRetryPause", producerRetryPause.toString())
  }

  /**
   * Where to replicate the data
   */
  public fun replicateTo(replicateTo: String) {
    it.property("replicateTo", replicateTo)
  }

  /**
   * Where to replicate the data
   */
  public fun replicateTo(replicateTo: Int) {
    it.property("replicateTo", replicateTo.toString())
  }

  /**
   * Define the starting Id where we are doing an insert operation
   */
  public fun startingIdForInsertsFrom(startingIdForInsertsFrom: String) {
    it.property("startingIdForInsertsFrom", startingIdForInsertsFrom)
  }

  /**
   * Define the starting Id where we are doing an insert operation
   */
  public fun startingIdForInsertsFrom(startingIdForInsertsFrom: Int) {
    it.property("startingIdForInsertsFrom", startingIdForInsertsFrom.toString())
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
   * The additional hosts
   */
  public fun additionalHosts(additionalHosts: String) {
    it.property("additionalHosts", additionalHosts)
  }

  /**
   * Define the timeoutconnect in milliseconds
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * Define the operation timeout in milliseconds
   */
  public fun queryTimeout(queryTimeout: String) {
    it.property("queryTimeout", queryTimeout)
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
   * The password to use
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * The username to use
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
