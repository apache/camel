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

public fun UriDsl.jms(i: JmsUriDsl.() -> Unit) {
  JmsUriDsl(this).apply(i)
}

@CamelDslMarker
public class JmsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jms")
  }

  private var destinationType: String = ""

  private var destinationName: String = ""

  public fun destinationType(destinationType: String) {
    this.destinationType = destinationType
    it.url("$destinationType:$destinationName")
  }

  public fun destinationName(destinationName: String) {
    this.destinationName = destinationName
    it.url("$destinationType:$destinationName")
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  public fun disableReplyTo(disableReplyTo: String) {
    it.property("disableReplyTo", disableReplyTo)
  }

  public fun disableReplyTo(disableReplyTo: Boolean) {
    it.property("disableReplyTo", disableReplyTo.toString())
  }

  public fun durableSubscriptionName(durableSubscriptionName: String) {
    it.property("durableSubscriptionName", durableSubscriptionName)
  }

  public fun jmsMessageType(jmsMessageType: String) {
    it.property("jmsMessageType", jmsMessageType)
  }

  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  public fun acknowledgementModeName(acknowledgementModeName: String) {
    it.property("acknowledgementModeName", acknowledgementModeName)
  }

  public fun artemisConsumerPriority(artemisConsumerPriority: String) {
    it.property("artemisConsumerPriority", artemisConsumerPriority)
  }

  public fun artemisConsumerPriority(artemisConsumerPriority: Int) {
    it.property("artemisConsumerPriority", artemisConsumerPriority.toString())
  }

  public fun asyncConsumer(asyncConsumer: String) {
    it.property("asyncConsumer", asyncConsumer)
  }

  public fun asyncConsumer(asyncConsumer: Boolean) {
    it.property("asyncConsumer", asyncConsumer.toString())
  }

  public fun autoStartup(autoStartup: String) {
    it.property("autoStartup", autoStartup)
  }

  public fun autoStartup(autoStartup: Boolean) {
    it.property("autoStartup", autoStartup.toString())
  }

  public fun cacheLevel(cacheLevel: String) {
    it.property("cacheLevel", cacheLevel)
  }

  public fun cacheLevel(cacheLevel: Int) {
    it.property("cacheLevel", cacheLevel.toString())
  }

  public fun cacheLevelName(cacheLevelName: String) {
    it.property("cacheLevelName", cacheLevelName)
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: String) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent)
  }

  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: Boolean) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent.toString())
  }

  public fun selector(selector: String) {
    it.property("selector", selector)
  }

  public fun subscriptionDurable(subscriptionDurable: String) {
    it.property("subscriptionDurable", subscriptionDurable)
  }

  public fun subscriptionDurable(subscriptionDurable: Boolean) {
    it.property("subscriptionDurable", subscriptionDurable.toString())
  }

  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
  }

  public fun subscriptionShared(subscriptionShared: String) {
    it.property("subscriptionShared", subscriptionShared)
  }

  public fun subscriptionShared(subscriptionShared: Boolean) {
    it.property("subscriptionShared", subscriptionShared.toString())
  }

  public fun acceptMessagesWhileStopping(acceptMessagesWhileStopping: String) {
    it.property("acceptMessagesWhileStopping", acceptMessagesWhileStopping)
  }

  public fun acceptMessagesWhileStopping(acceptMessagesWhileStopping: Boolean) {
    it.property("acceptMessagesWhileStopping", acceptMessagesWhileStopping.toString())
  }

  public fun allowReplyManagerQuickStop(allowReplyManagerQuickStop: String) {
    it.property("allowReplyManagerQuickStop", allowReplyManagerQuickStop)
  }

  public fun allowReplyManagerQuickStop(allowReplyManagerQuickStop: Boolean) {
    it.property("allowReplyManagerQuickStop", allowReplyManagerQuickStop.toString())
  }

  public fun consumerType(consumerType: String) {
    it.property("consumerType", consumerType)
  }

  public fun defaultTaskExecutorType(defaultTaskExecutorType: String) {
    it.property("defaultTaskExecutorType", defaultTaskExecutorType)
  }

  public fun eagerLoadingOfProperties(eagerLoadingOfProperties: String) {
    it.property("eagerLoadingOfProperties", eagerLoadingOfProperties)
  }

  public fun eagerLoadingOfProperties(eagerLoadingOfProperties: Boolean) {
    it.property("eagerLoadingOfProperties", eagerLoadingOfProperties.toString())
  }

  public fun eagerPoisonBody(eagerPoisonBody: String) {
    it.property("eagerPoisonBody", eagerPoisonBody)
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun exposeListenerSession(exposeListenerSession: String) {
    it.property("exposeListenerSession", exposeListenerSession)
  }

  public fun exposeListenerSession(exposeListenerSession: Boolean) {
    it.property("exposeListenerSession", exposeListenerSession.toString())
  }

  public fun replyToConsumerType(replyToConsumerType: String) {
    it.property("replyToConsumerType", replyToConsumerType)
  }

  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: String) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed)
  }

  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: Boolean) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed.toString())
  }

  public fun taskExecutor(taskExecutor: String) {
    it.property("taskExecutor", taskExecutor)
  }

  public fun deliveryDelay(deliveryDelay: String) {
    it.property("deliveryDelay", deliveryDelay)
  }

  public fun deliveryDelay(deliveryDelay: Int) {
    it.property("deliveryDelay", deliveryDelay.toString())
  }

  public fun deliveryMode(deliveryMode: String) {
    it.property("deliveryMode", deliveryMode)
  }

  public fun deliveryMode(deliveryMode: Int) {
    it.property("deliveryMode", deliveryMode.toString())
  }

  public fun deliveryPersistent(deliveryPersistent: String) {
    it.property("deliveryPersistent", deliveryPersistent)
  }

  public fun deliveryPersistent(deliveryPersistent: Boolean) {
    it.property("deliveryPersistent", deliveryPersistent.toString())
  }

  public fun explicitQosEnabled(explicitQosEnabled: String) {
    it.property("explicitQosEnabled", explicitQosEnabled)
  }

  public fun explicitQosEnabled(explicitQosEnabled: Boolean) {
    it.property("explicitQosEnabled", explicitQosEnabled.toString())
  }

  public fun formatDateHeadersToIso8601(formatDateHeadersToIso8601: String) {
    it.property("formatDateHeadersToIso8601", formatDateHeadersToIso8601)
  }

  public fun formatDateHeadersToIso8601(formatDateHeadersToIso8601: Boolean) {
    it.property("formatDateHeadersToIso8601", formatDateHeadersToIso8601.toString())
  }

  public fun preserveMessageQos(preserveMessageQos: String) {
    it.property("preserveMessageQos", preserveMessageQos)
  }

  public fun preserveMessageQos(preserveMessageQos: Boolean) {
    it.property("preserveMessageQos", preserveMessageQos.toString())
  }

  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  public fun priority(priority: Int) {
    it.property("priority", priority.toString())
  }

  public fun replyToConcurrentConsumers(replyToConcurrentConsumers: String) {
    it.property("replyToConcurrentConsumers", replyToConcurrentConsumers)
  }

  public fun replyToConcurrentConsumers(replyToConcurrentConsumers: Int) {
    it.property("replyToConcurrentConsumers", replyToConcurrentConsumers.toString())
  }

  public fun replyToMaxConcurrentConsumers(replyToMaxConcurrentConsumers: String) {
    it.property("replyToMaxConcurrentConsumers", replyToMaxConcurrentConsumers)
  }

  public fun replyToMaxConcurrentConsumers(replyToMaxConcurrentConsumers: Int) {
    it.property("replyToMaxConcurrentConsumers", replyToMaxConcurrentConsumers.toString())
  }

  public
      fun replyToOnTimeoutMaxConcurrentConsumers(replyToOnTimeoutMaxConcurrentConsumers: String) {
    it.property("replyToOnTimeoutMaxConcurrentConsumers", replyToOnTimeoutMaxConcurrentConsumers)
  }

  public fun replyToOnTimeoutMaxConcurrentConsumers(replyToOnTimeoutMaxConcurrentConsumers: Int) {
    it.property("replyToOnTimeoutMaxConcurrentConsumers",
        replyToOnTimeoutMaxConcurrentConsumers.toString())
  }

  public fun replyToOverride(replyToOverride: String) {
    it.property("replyToOverride", replyToOverride)
  }

  public fun replyToType(replyToType: String) {
    it.property("replyToType", replyToType)
  }

  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  public fun timeToLive(timeToLive: String) {
    it.property("timeToLive", timeToLive)
  }

  public fun timeToLive(timeToLive: Int) {
    it.property("timeToLive", timeToLive.toString())
  }

  public fun allowAdditionalHeaders(allowAdditionalHeaders: String) {
    it.property("allowAdditionalHeaders", allowAdditionalHeaders)
  }

  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  public fun alwaysCopyMessage(alwaysCopyMessage: String) {
    it.property("alwaysCopyMessage", alwaysCopyMessage)
  }

  public fun alwaysCopyMessage(alwaysCopyMessage: Boolean) {
    it.property("alwaysCopyMessage", alwaysCopyMessage.toString())
  }

  public fun correlationProperty(correlationProperty: String) {
    it.property("correlationProperty", correlationProperty)
  }

  public fun disableTimeToLive(disableTimeToLive: String) {
    it.property("disableTimeToLive", disableTimeToLive)
  }

  public fun disableTimeToLive(disableTimeToLive: Boolean) {
    it.property("disableTimeToLive", disableTimeToLive.toString())
  }

  public fun forceSendOriginalMessage(forceSendOriginalMessage: String) {
    it.property("forceSendOriginalMessage", forceSendOriginalMessage)
  }

  public fun forceSendOriginalMessage(forceSendOriginalMessage: Boolean) {
    it.property("forceSendOriginalMessage", forceSendOriginalMessage.toString())
  }

  public fun includeSentJMSMessageID(includeSentJMSMessageID: String) {
    it.property("includeSentJMSMessageID", includeSentJMSMessageID)
  }

  public fun includeSentJMSMessageID(includeSentJMSMessageID: Boolean) {
    it.property("includeSentJMSMessageID", includeSentJMSMessageID.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun replyToCacheLevelName(replyToCacheLevelName: String) {
    it.property("replyToCacheLevelName", replyToCacheLevelName)
  }

  public fun replyToDestinationSelectorName(replyToDestinationSelectorName: String) {
    it.property("replyToDestinationSelectorName", replyToDestinationSelectorName)
  }

  public fun streamMessageTypeEnabled(streamMessageTypeEnabled: String) {
    it.property("streamMessageTypeEnabled", streamMessageTypeEnabled)
  }

  public fun streamMessageTypeEnabled(streamMessageTypeEnabled: Boolean) {
    it.property("streamMessageTypeEnabled", streamMessageTypeEnabled.toString())
  }

  public fun allowSerializedHeaders(allowSerializedHeaders: String) {
    it.property("allowSerializedHeaders", allowSerializedHeaders)
  }

  public fun allowSerializedHeaders(allowSerializedHeaders: Boolean) {
    it.property("allowSerializedHeaders", allowSerializedHeaders.toString())
  }

  public fun artemisStreamingEnabled(artemisStreamingEnabled: String) {
    it.property("artemisStreamingEnabled", artemisStreamingEnabled)
  }

  public fun artemisStreamingEnabled(artemisStreamingEnabled: Boolean) {
    it.property("artemisStreamingEnabled", artemisStreamingEnabled.toString())
  }

  public fun asyncStartListener(asyncStartListener: String) {
    it.property("asyncStartListener", asyncStartListener)
  }

  public fun asyncStartListener(asyncStartListener: Boolean) {
    it.property("asyncStartListener", asyncStartListener.toString())
  }

  public fun asyncStopListener(asyncStopListener: String) {
    it.property("asyncStopListener", asyncStopListener)
  }

  public fun asyncStopListener(asyncStopListener: Boolean) {
    it.property("asyncStopListener", asyncStopListener.toString())
  }

  public fun destinationResolver(destinationResolver: String) {
    it.property("destinationResolver", destinationResolver)
  }

  public fun errorHandler(errorHandler: String) {
    it.property("errorHandler", errorHandler)
  }

  public fun exceptionListener(exceptionListener: String) {
    it.property("exceptionListener", exceptionListener)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun idleConsumerLimit(idleConsumerLimit: String) {
    it.property("idleConsumerLimit", idleConsumerLimit)
  }

  public fun idleConsumerLimit(idleConsumerLimit: Int) {
    it.property("idleConsumerLimit", idleConsumerLimit.toString())
  }

  public fun idleTaskExecutionLimit(idleTaskExecutionLimit: String) {
    it.property("idleTaskExecutionLimit", idleTaskExecutionLimit)
  }

  public fun idleTaskExecutionLimit(idleTaskExecutionLimit: Int) {
    it.property("idleTaskExecutionLimit", idleTaskExecutionLimit.toString())
  }

  public fun includeAllJMSXProperties(includeAllJMSXProperties: String) {
    it.property("includeAllJMSXProperties", includeAllJMSXProperties)
  }

  public fun includeAllJMSXProperties(includeAllJMSXProperties: Boolean) {
    it.property("includeAllJMSXProperties", includeAllJMSXProperties.toString())
  }

  public fun jmsKeyFormatStrategy(jmsKeyFormatStrategy: String) {
    it.property("jmsKeyFormatStrategy", jmsKeyFormatStrategy)
  }

  public fun mapJmsMessage(mapJmsMessage: String) {
    it.property("mapJmsMessage", mapJmsMessage)
  }

  public fun mapJmsMessage(mapJmsMessage: Boolean) {
    it.property("mapJmsMessage", mapJmsMessage.toString())
  }

  public fun maxMessagesPerTask(maxMessagesPerTask: String) {
    it.property("maxMessagesPerTask", maxMessagesPerTask)
  }

  public fun maxMessagesPerTask(maxMessagesPerTask: Int) {
    it.property("maxMessagesPerTask", maxMessagesPerTask.toString())
  }

  public fun messageConverter(messageConverter: String) {
    it.property("messageConverter", messageConverter)
  }

  public fun messageCreatedStrategy(messageCreatedStrategy: String) {
    it.property("messageCreatedStrategy", messageCreatedStrategy)
  }

  public fun messageIdEnabled(messageIdEnabled: String) {
    it.property("messageIdEnabled", messageIdEnabled)
  }

  public fun messageIdEnabled(messageIdEnabled: Boolean) {
    it.property("messageIdEnabled", messageIdEnabled.toString())
  }

  public fun messageListenerContainerFactory(messageListenerContainerFactory: String) {
    it.property("messageListenerContainerFactory", messageListenerContainerFactory)
  }

  public fun messageTimestampEnabled(messageTimestampEnabled: String) {
    it.property("messageTimestampEnabled", messageTimestampEnabled)
  }

  public fun messageTimestampEnabled(messageTimestampEnabled: Boolean) {
    it.property("messageTimestampEnabled", messageTimestampEnabled.toString())
  }

  public fun pubSubNoLocal(pubSubNoLocal: String) {
    it.property("pubSubNoLocal", pubSubNoLocal)
  }

  public fun pubSubNoLocal(pubSubNoLocal: Boolean) {
    it.property("pubSubNoLocal", pubSubNoLocal.toString())
  }

  public fun receiveTimeout(receiveTimeout: String) {
    it.property("receiveTimeout", receiveTimeout)
  }

  public fun recoveryInterval(recoveryInterval: String) {
    it.property("recoveryInterval", recoveryInterval)
  }

  public fun requestTimeoutCheckerInterval(requestTimeoutCheckerInterval: String) {
    it.property("requestTimeoutCheckerInterval", requestTimeoutCheckerInterval)
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  public fun useMessageIDAsCorrelationID(useMessageIDAsCorrelationID: String) {
    it.property("useMessageIDAsCorrelationID", useMessageIDAsCorrelationID)
  }

  public fun useMessageIDAsCorrelationID(useMessageIDAsCorrelationID: Boolean) {
    it.property("useMessageIDAsCorrelationID", useMessageIDAsCorrelationID.toString())
  }

  public
      fun waitForProvisionCorrelationToBeUpdatedCounter(waitForProvisionCorrelationToBeUpdatedCounter: String) {
    it.property("waitForProvisionCorrelationToBeUpdatedCounter",
        waitForProvisionCorrelationToBeUpdatedCounter)
  }

  public
      fun waitForProvisionCorrelationToBeUpdatedCounter(waitForProvisionCorrelationToBeUpdatedCounter: Int) {
    it.property("waitForProvisionCorrelationToBeUpdatedCounter",
        waitForProvisionCorrelationToBeUpdatedCounter.toString())
  }

  public
      fun waitForProvisionCorrelationToBeUpdatedThreadSleepingTime(waitForProvisionCorrelationToBeUpdatedThreadSleepingTime: String) {
    it.property("waitForProvisionCorrelationToBeUpdatedThreadSleepingTime",
        waitForProvisionCorrelationToBeUpdatedThreadSleepingTime)
  }

  public fun errorHandlerLoggingLevel(errorHandlerLoggingLevel: String) {
    it.property("errorHandlerLoggingLevel", errorHandlerLoggingLevel)
  }

  public fun errorHandlerLogStackTrace(errorHandlerLogStackTrace: String) {
    it.property("errorHandlerLogStackTrace", errorHandlerLogStackTrace)
  }

  public fun errorHandlerLogStackTrace(errorHandlerLogStackTrace: Boolean) {
    it.property("errorHandlerLogStackTrace", errorHandlerLogStackTrace.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  public fun transactedInOut(transactedInOut: String) {
    it.property("transactedInOut", transactedInOut)
  }

  public fun transactedInOut(transactedInOut: Boolean) {
    it.property("transactedInOut", transactedInOut.toString())
  }

  public fun lazyCreateTransactionManager(lazyCreateTransactionManager: String) {
    it.property("lazyCreateTransactionManager", lazyCreateTransactionManager)
  }

  public fun lazyCreateTransactionManager(lazyCreateTransactionManager: Boolean) {
    it.property("lazyCreateTransactionManager", lazyCreateTransactionManager.toString())
  }

  public fun transactionManager(transactionManager: String) {
    it.property("transactionManager", transactionManager)
  }

  public fun transactionName(transactionName: String) {
    it.property("transactionName", transactionName)
  }

  public fun transactionTimeout(transactionTimeout: String) {
    it.property("transactionTimeout", transactionTimeout)
  }

  public fun transactionTimeout(transactionTimeout: Int) {
    it.property("transactionTimeout", transactionTimeout.toString())
  }
}
