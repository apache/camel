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

public fun UriDsl.jpa(i: JpaUriDsl.() -> Unit) {
  JpaUriDsl(this).apply(i)
}

@CamelDslMarker
public class JpaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jpa")
  }

  private var entityType: String = ""

  public fun entityType(entityType: String) {
    this.entityType = entityType
    it.url("$entityType")
  }

  public fun joinTransaction(joinTransaction: String) {
    it.property("joinTransaction", joinTransaction)
  }

  public fun joinTransaction(joinTransaction: Boolean) {
    it.property("joinTransaction", joinTransaction.toString())
  }

  public fun maximumResults(maximumResults: String) {
    it.property("maximumResults", maximumResults)
  }

  public fun maximumResults(maximumResults: Int) {
    it.property("maximumResults", maximumResults.toString())
  }

  public fun namedQuery(namedQuery: String) {
    it.property("namedQuery", namedQuery)
  }

  public fun nativeQuery(nativeQuery: String) {
    it.property("nativeQuery", nativeQuery)
  }

  public fun persistenceUnit(persistenceUnit: String) {
    it.property("persistenceUnit", persistenceUnit)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun resultClass(resultClass: String) {
    it.property("resultClass", resultClass)
  }

  public fun consumeDelete(consumeDelete: String) {
    it.property("consumeDelete", consumeDelete)
  }

  public fun consumeDelete(consumeDelete: Boolean) {
    it.property("consumeDelete", consumeDelete.toString())
  }

  public fun consumeLockEntity(consumeLockEntity: String) {
    it.property("consumeLockEntity", consumeLockEntity)
  }

  public fun consumeLockEntity(consumeLockEntity: Boolean) {
    it.property("consumeLockEntity", consumeLockEntity.toString())
  }

  public fun deleteHandler(deleteHandler: String) {
    it.property("deleteHandler", deleteHandler)
  }

  public fun lockModeType(lockModeType: String) {
    it.property("lockModeType", lockModeType)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun preDeleteHandler(preDeleteHandler: String) {
    it.property("preDeleteHandler", preDeleteHandler)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun skipLockedEntity(skipLockedEntity: String) {
    it.property("skipLockedEntity", skipLockedEntity)
  }

  public fun skipLockedEntity(skipLockedEntity: Boolean) {
    it.property("skipLockedEntity", skipLockedEntity.toString())
  }

  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
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

  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun findEntity(findEntity: String) {
    it.property("findEntity", findEntity)
  }

  public fun findEntity(findEntity: Boolean) {
    it.property("findEntity", findEntity.toString())
  }

  public fun firstResult(firstResult: String) {
    it.property("firstResult", firstResult)
  }

  public fun firstResult(firstResult: Int) {
    it.property("firstResult", firstResult.toString())
  }

  public fun flushOnSend(flushOnSend: String) {
    it.property("flushOnSend", flushOnSend)
  }

  public fun flushOnSend(flushOnSend: Boolean) {
    it.property("flushOnSend", flushOnSend.toString())
  }

  public fun outputTarget(outputTarget: String) {
    it.property("outputTarget", outputTarget)
  }

  public fun remove(remove: String) {
    it.property("remove", remove)
  }

  public fun remove(remove: Boolean) {
    it.property("remove", remove.toString())
  }

  public fun singleResult(singleResult: String) {
    it.property("singleResult", singleResult)
  }

  public fun singleResult(singleResult: Boolean) {
    it.property("singleResult", singleResult.toString())
  }

  public fun useExecuteUpdate(useExecuteUpdate: String) {
    it.property("useExecuteUpdate", useExecuteUpdate)
  }

  public fun useExecuteUpdate(useExecuteUpdate: Boolean) {
    it.property("useExecuteUpdate", useExecuteUpdate.toString())
  }

  public fun usePersist(usePersist: String) {
    it.property("usePersist", usePersist)
  }

  public fun usePersist(usePersist: Boolean) {
    it.property("usePersist", usePersist.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun usePassedInEntityManager(usePassedInEntityManager: String) {
    it.property("usePassedInEntityManager", usePassedInEntityManager)
  }

  public fun usePassedInEntityManager(usePassedInEntityManager: Boolean) {
    it.property("usePassedInEntityManager", usePassedInEntityManager.toString())
  }

  public fun entityManagerProperties(entityManagerProperties: String) {
    it.property("entityManagerProperties", entityManagerProperties)
  }

  public fun sharedEntityManager(sharedEntityManager: String) {
    it.property("sharedEntityManager", sharedEntityManager)
  }

  public fun sharedEntityManager(sharedEntityManager: Boolean) {
    it.property("sharedEntityManager", sharedEntityManager.toString())
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
