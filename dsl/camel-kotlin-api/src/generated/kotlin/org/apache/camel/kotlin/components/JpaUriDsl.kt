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
 * Store and retrieve Java objects from databases using Java Persistence API (JPA).
 */
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

  /**
   * Entity class name
   */
  public fun entityType(entityType: String) {
    this.entityType = entityType
    it.url("$entityType")
  }

  /**
   * The camel-jpa component will join transaction by default. You can use this option to turn this
   * off, for example, if you use LOCAL_RESOURCE and join transaction doesn't work with your JPA
   * provider. This option can also be set globally on the JpaComponent, instead of having to set it on
   * all endpoints.
   */
  public fun joinTransaction(joinTransaction: String) {
    it.property("joinTransaction", joinTransaction)
  }

  /**
   * The camel-jpa component will join transaction by default. You can use this option to turn this
   * off, for example, if you use LOCAL_RESOURCE and join transaction doesn't work with your JPA
   * provider. This option can also be set globally on the JpaComponent, instead of having to set it on
   * all endpoints.
   */
  public fun joinTransaction(joinTransaction: Boolean) {
    it.property("joinTransaction", joinTransaction.toString())
  }

  /**
   * Set the maximum number of results to retrieve on the Query.
   */
  public fun maximumResults(maximumResults: String) {
    it.property("maximumResults", maximumResults)
  }

  /**
   * Set the maximum number of results to retrieve on the Query.
   */
  public fun maximumResults(maximumResults: Int) {
    it.property("maximumResults", maximumResults.toString())
  }

  /**
   * To use a named query.
   */
  public fun namedQuery(namedQuery: String) {
    it.property("namedQuery", namedQuery)
  }

  /**
   * To use a custom native query. You may want to use the option resultClass also when using native
   * queries.
   */
  public fun nativeQuery(nativeQuery: String) {
    it.property("nativeQuery", nativeQuery)
  }

  /**
   * The JPA persistence unit used by default.
   */
  public fun persistenceUnit(persistenceUnit: String) {
    it.property("persistenceUnit", persistenceUnit)
  }

  /**
   * To use a custom query.
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * Defines the type of the returned payload (we will call
   * entityManager.createNativeQuery(nativeQuery, resultClass) instead of
   * entityManager.createNativeQuery(nativeQuery)). Without this option, we will return an object
   * array. Only has an effect when using in conjunction with a native query when consuming data.
   */
  public fun resultClass(resultClass: String) {
    it.property("resultClass", resultClass)
  }

  /**
   * If true, the entity is deleted after it is consumed; if false, the entity is not deleted.
   */
  public fun consumeDelete(consumeDelete: String) {
    it.property("consumeDelete", consumeDelete)
  }

  /**
   * If true, the entity is deleted after it is consumed; if false, the entity is not deleted.
   */
  public fun consumeDelete(consumeDelete: Boolean) {
    it.property("consumeDelete", consumeDelete.toString())
  }

  /**
   * Specifies whether to set an exclusive lock on each entity bean while processing the results
   * from polling.
   */
  public fun consumeLockEntity(consumeLockEntity: String) {
    it.property("consumeLockEntity", consumeLockEntity)
  }

  /**
   * Specifies whether to set an exclusive lock on each entity bean while processing the results
   * from polling.
   */
  public fun consumeLockEntity(consumeLockEntity: Boolean) {
    it.property("consumeLockEntity", consumeLockEntity.toString())
  }

  /**
   * To use a custom DeleteHandler to delete the row after the consumer is done processing the
   * exchange
   */
  public fun deleteHandler(deleteHandler: String) {
    it.property("deleteHandler", deleteHandler)
  }

  /**
   * To configure the lock mode on the consumer.
   */
  public fun lockModeType(lockModeType: String) {
    it.property("lockModeType", lockModeType)
  }

  /**
   * An integer value to define the maximum number of messages to gather per poll. By default, no
   * maximum is set. It can be used to avoid polling many thousands of messages when starting up the
   * server. Set a value of 0 or negative to disable.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * An integer value to define the maximum number of messages to gather per poll. By default, no
   * maximum is set. It can be used to avoid polling many thousands of messages when starting up the
   * server. Set a value of 0 or negative to disable.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * To use a custom Pre-DeleteHandler to delete the row after the consumer has read the entity.
   */
  public fun preDeleteHandler(preDeleteHandler: String) {
    it.property("preDeleteHandler", preDeleteHandler)
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
   * To configure whether to use NOWAIT on lock and silently skip the entity.
   */
  public fun skipLockedEntity(skipLockedEntity: String) {
    it.property("skipLockedEntity", skipLockedEntity)
  }

  /**
   * To configure whether to use NOWAIT on lock and silently skip the entity.
   */
  public fun skipLockedEntity(skipLockedEntity: Boolean) {
    it.property("skipLockedEntity", skipLockedEntity.toString())
  }

  /**
   * Whether to run the consumer in transacted mode, by which all messages will either commit or
   * rollback, when the entire batch has been processed. The default behavior (false) is to commit all
   * the previously successfully processed messages, and only roll back the last failed message.
   */
  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  /**
   * Whether to run the consumer in transacted mode, by which all messages will either commit or
   * rollback, when the entire batch has been processed. The default behavior (false) is to commit all
   * the previously successfully processed messages, and only roll back the last failed message.
   */
  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
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
   * This key/value mapping is used for building the query parameters. It is expected to be of the
   * generic type java.util.Map where the keys are the named parameters of a given JPA query and the
   * values are their corresponding effective values you want to select for. When it's used for
   * producer, Simple expression can be used as a parameter value. It allows you to retrieve parameter
   * values from the message body, header and etc.
   */
  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
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
   * If enabled, then the producer will find a single entity by using the message body as a key and
   * entityType as the class type. This can be used instead of a query to find a single entity.
   */
  public fun findEntity(findEntity: String) {
    it.property("findEntity", findEntity)
  }

  /**
   * If enabled, then the producer will find a single entity by using the message body as a key and
   * entityType as the class type. This can be used instead of a query to find a single entity.
   */
  public fun findEntity(findEntity: Boolean) {
    it.property("findEntity", findEntity.toString())
  }

  /**
   * Set the position of the first result to retrieve.
   */
  public fun firstResult(firstResult: String) {
    it.property("firstResult", firstResult)
  }

  /**
   * Set the position of the first result to retrieve.
   */
  public fun firstResult(firstResult: Int) {
    it.property("firstResult", firstResult.toString())
  }

  /**
   * Flushes the EntityManager after the entity bean has been persisted.
   */
  public fun flushOnSend(flushOnSend: String) {
    it.property("flushOnSend", flushOnSend)
  }

  /**
   * Flushes the EntityManager after the entity bean has been persisted.
   */
  public fun flushOnSend(flushOnSend: Boolean) {
    it.property("flushOnSend", flushOnSend.toString())
  }

  /**
   * To put the query (or find) result in a header or property instead of the body. If the value
   * starts with the prefix property:, put the result into the so named property, otherwise into the
   * header.
   */
  public fun outputTarget(outputTarget: String) {
    it.property("outputTarget", outputTarget)
  }

  /**
   * Indicates to use entityManager.remove(entity).
   */
  public fun remove(remove: String) {
    it.property("remove", remove)
  }

  /**
   * Indicates to use entityManager.remove(entity).
   */
  public fun remove(remove: Boolean) {
    it.property("remove", remove.toString())
  }

  /**
   * If enabled, a query or a find which would return no results or more than one result, will throw
   * an exception instead.
   */
  public fun singleResult(singleResult: String) {
    it.property("singleResult", singleResult)
  }

  /**
   * If enabled, a query or a find which would return no results or more than one result, will throw
   * an exception instead.
   */
  public fun singleResult(singleResult: Boolean) {
    it.property("singleResult", singleResult.toString())
  }

  /**
   * To configure whether to use executeUpdate() when producer executes a query. When you use
   * INSERT, UPDATE or a DELETE statement as a named query, you need to specify this option to 'true'.
   */
  public fun useExecuteUpdate(useExecuteUpdate: String) {
    it.property("useExecuteUpdate", useExecuteUpdate)
  }

  /**
   * To configure whether to use executeUpdate() when producer executes a query. When you use
   * INSERT, UPDATE or a DELETE statement as a named query, you need to specify this option to 'true'.
   */
  public fun useExecuteUpdate(useExecuteUpdate: Boolean) {
    it.property("useExecuteUpdate", useExecuteUpdate.toString())
  }

  /**
   * Indicates to use entityManager.persist(entity) instead of entityManager.merge(entity). Note:
   * entityManager.persist(entity) doesn't work for detached entities (where the EntityManager has to
   * execute an UPDATE instead of an INSERT query)!
   */
  public fun usePersist(usePersist: String) {
    it.property("usePersist", usePersist)
  }

  /**
   * Indicates to use entityManager.persist(entity) instead of entityManager.merge(entity). Note:
   * entityManager.persist(entity) doesn't work for detached entities (where the EntityManager has to
   * execute an UPDATE instead of an INSERT query)!
   */
  public fun usePersist(usePersist: Boolean) {
    it.property("usePersist", usePersist.toString())
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
   * If set to true, then Camel will use the EntityManager from the header
   * JpaConstants.ENTITY_MANAGER instead of the configured entity manager on the component/endpoint.
   * This allows end users to control which entity manager will be in use.
   */
  public fun usePassedInEntityManager(usePassedInEntityManager: String) {
    it.property("usePassedInEntityManager", usePassedInEntityManager)
  }

  /**
   * If set to true, then Camel will use the EntityManager from the header
   * JpaConstants.ENTITY_MANAGER instead of the configured entity manager on the component/endpoint.
   * This allows end users to control which entity manager will be in use.
   */
  public fun usePassedInEntityManager(usePassedInEntityManager: Boolean) {
    it.property("usePassedInEntityManager", usePassedInEntityManager.toString())
  }

  /**
   * Additional properties for the entity manager to use.
   */
  public fun entityManagerProperties(entityManagerProperties: String) {
    it.property("entityManagerProperties", entityManagerProperties)
  }

  /**
   * Whether to use Spring's SharedEntityManager for the consumer/producer. Note in most cases,
   * joinTransaction should be set to false as this is not an EXTENDED EntityManager.
   */
  public fun sharedEntityManager(sharedEntityManager: String) {
    it.property("sharedEntityManager", sharedEntityManager)
  }

  /**
   * Whether to use Spring's SharedEntityManager for the consumer/producer. Note in most cases,
   * joinTransaction should be set to false as this is not an EXTENDED EntityManager.
   */
  public fun sharedEntityManager(sharedEntityManager: Boolean) {
    it.property("sharedEntityManager", sharedEntityManager.toString())
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
}
