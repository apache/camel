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

  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol://$hostname:$port")
  }

  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$protocol://$hostname:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$protocol://$hostname:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol://$hostname:$port")
  }

  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  public fun collection(collection: String) {
    it.property("collection", collection)
  }

  public fun key(key: String) {
    it.property("key", key)
  }

  public fun scope(scope: String) {
    it.property("scope", scope)
  }

  public fun consumerProcessedStrategy(consumerProcessedStrategy: String) {
    it.property("consumerProcessedStrategy", consumerProcessedStrategy)
  }

  public fun descending(descending: String) {
    it.property("descending", descending)
  }

  public fun descending(descending: Boolean) {
    it.property("descending", descending.toString())
  }

  public fun designDocumentName(designDocumentName: String) {
    it.property("designDocumentName", designDocumentName)
  }

  public fun fullDocument(fullDocument: String) {
    it.property("fullDocument", fullDocument)
  }

  public fun fullDocument(fullDocument: Boolean) {
    it.property("fullDocument", fullDocument.toString())
  }

  public fun limit(limit: String) {
    it.property("limit", limit)
  }

  public fun limit(limit: Int) {
    it.property("limit", limit.toString())
  }

  public fun rangeEndKey(rangeEndKey: String) {
    it.property("rangeEndKey", rangeEndKey)
  }

  public fun rangeStartKey(rangeStartKey: String) {
    it.property("rangeStartKey", rangeStartKey)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun skip(skip: String) {
    it.property("skip", skip)
  }

  public fun skip(skip: Int) {
    it.property("skip", skip.toString())
  }

  public fun viewName(viewName: String) {
    it.property("viewName", viewName)
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

  public fun autoStartIdForInserts(autoStartIdForInserts: String) {
    it.property("autoStartIdForInserts", autoStartIdForInserts)
  }

  public fun autoStartIdForInserts(autoStartIdForInserts: Boolean) {
    it.property("autoStartIdForInserts", autoStartIdForInserts.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun persistTo(persistTo: String) {
    it.property("persistTo", persistTo)
  }

  public fun persistTo(persistTo: Int) {
    it.property("persistTo", persistTo.toString())
  }

  public fun producerRetryAttempts(producerRetryAttempts: String) {
    it.property("producerRetryAttempts", producerRetryAttempts)
  }

  public fun producerRetryAttempts(producerRetryAttempts: Int) {
    it.property("producerRetryAttempts", producerRetryAttempts.toString())
  }

  public fun producerRetryPause(producerRetryPause: String) {
    it.property("producerRetryPause", producerRetryPause)
  }

  public fun producerRetryPause(producerRetryPause: Int) {
    it.property("producerRetryPause", producerRetryPause.toString())
  }

  public fun replicateTo(replicateTo: String) {
    it.property("replicateTo", replicateTo)
  }

  public fun replicateTo(replicateTo: Int) {
    it.property("replicateTo", replicateTo.toString())
  }

  public fun startingIdForInsertsFrom(startingIdForInsertsFrom: String) {
    it.property("startingIdForInsertsFrom", startingIdForInsertsFrom)
  }

  public fun startingIdForInsertsFrom(startingIdForInsertsFrom: Int) {
    it.property("startingIdForInsertsFrom", startingIdForInsertsFrom.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun additionalHosts(additionalHosts: String) {
    it.property("additionalHosts", additionalHosts)
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun queryTimeout(queryTimeout: String) {
    it.property("queryTimeout", queryTimeout)
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

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
