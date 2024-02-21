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

public fun UriDsl.sql(i: SqlUriDsl.() -> Unit) {
  SqlUriDsl(this).apply(i)
}

@CamelDslMarker
public class SqlUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sql")
  }

  private var query: String = ""

  public fun query(query: String) {
    this.query = query
    it.url("$query")
  }

  public fun allowNamedParameters(allowNamedParameters: String) {
    it.property("allowNamedParameters", allowNamedParameters)
  }

  public fun allowNamedParameters(allowNamedParameters: Boolean) {
    it.property("allowNamedParameters", allowNamedParameters.toString())
  }

  public fun dataSource(dataSource: String) {
    it.property("dataSource", dataSource)
  }

  public fun outputClass(outputClass: String) {
    it.property("outputClass", outputClass)
  }

  public fun outputHeader(outputHeader: String) {
    it.property("outputHeader", outputHeader)
  }

  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  public fun separator(separator: String) {
    it.property("separator", separator)
  }

  public fun breakBatchOnConsumeFail(breakBatchOnConsumeFail: String) {
    it.property("breakBatchOnConsumeFail", breakBatchOnConsumeFail)
  }

  public fun breakBatchOnConsumeFail(breakBatchOnConsumeFail: Boolean) {
    it.property("breakBatchOnConsumeFail", breakBatchOnConsumeFail.toString())
  }

  public fun expectedUpdateCount(expectedUpdateCount: String) {
    it.property("expectedUpdateCount", expectedUpdateCount)
  }

  public fun expectedUpdateCount(expectedUpdateCount: Int) {
    it.property("expectedUpdateCount", expectedUpdateCount.toString())
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun onConsume(onConsume: String) {
    it.property("onConsume", onConsume)
  }

  public fun onConsumeBatchComplete(onConsumeBatchComplete: String) {
    it.property("onConsumeBatchComplete", onConsumeBatchComplete)
  }

  public fun onConsumeFailed(onConsumeFailed: String) {
    it.property("onConsumeFailed", onConsumeFailed)
  }

  public fun routeEmptyResultSet(routeEmptyResultSet: String) {
    it.property("routeEmptyResultSet", routeEmptyResultSet)
  }

  public fun routeEmptyResultSet(routeEmptyResultSet: Boolean) {
    it.property("routeEmptyResultSet", routeEmptyResultSet.toString())
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  public fun useIterator(useIterator: String) {
    it.property("useIterator", useIterator)
  }

  public fun useIterator(useIterator: Boolean) {
    it.property("useIterator", useIterator.toString())
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

  public fun processingStrategy(processingStrategy: String) {
    it.property("processingStrategy", processingStrategy)
  }

  public fun batch(batch: String) {
    it.property("batch", batch)
  }

  public fun batch(batch: Boolean) {
    it.property("batch", batch.toString())
  }

  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  public fun useMessageBodyForSql(useMessageBodyForSql: String) {
    it.property("useMessageBodyForSql", useMessageBodyForSql)
  }

  public fun useMessageBodyForSql(useMessageBodyForSql: Boolean) {
    it.property("useMessageBodyForSql", useMessageBodyForSql.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun alwaysPopulateStatement(alwaysPopulateStatement: String) {
    it.property("alwaysPopulateStatement", alwaysPopulateStatement)
  }

  public fun alwaysPopulateStatement(alwaysPopulateStatement: Boolean) {
    it.property("alwaysPopulateStatement", alwaysPopulateStatement.toString())
  }

  public fun parametersCount(parametersCount: String) {
    it.property("parametersCount", parametersCount)
  }

  public fun parametersCount(parametersCount: Int) {
    it.property("parametersCount", parametersCount.toString())
  }

  public fun placeholder(placeholder: String) {
    it.property("placeholder", placeholder)
  }

  public fun prepareStatementStrategy(prepareStatementStrategy: String) {
    it.property("prepareStatementStrategy", prepareStatementStrategy)
  }

  public fun rowMapperFactory(rowMapperFactory: String) {
    it.property("rowMapperFactory", rowMapperFactory)
  }

  public fun templateOptions(templateOptions: String) {
    it.property("templateOptions", templateOptions)
  }

  public fun usePlaceholder(usePlaceholder: String) {
    it.property("usePlaceholder", usePlaceholder)
  }

  public fun usePlaceholder(usePlaceholder: Boolean) {
    it.property("usePlaceholder", usePlaceholder.toString())
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
}
