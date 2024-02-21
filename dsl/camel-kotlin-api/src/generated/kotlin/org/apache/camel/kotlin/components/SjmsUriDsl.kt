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

public fun UriDsl.sjms(i: SjmsUriDsl.() -> Unit) {
  SjmsUriDsl(this).apply(i)
}

@CamelDslMarker
public class SjmsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sjms")
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

  public fun acknowledgementMode(acknowledgementMode: String) {
    it.property("acknowledgementMode", acknowledgementMode)
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

  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
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

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun durableSubscriptionName(durableSubscriptionName: String) {
    it.property("durableSubscriptionName", durableSubscriptionName)
  }

  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: String) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent)
  }

  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: Boolean) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
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

  public fun messageSelector(messageSelector: String) {
    it.property("messageSelector", messageSelector)
  }

  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: String) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed)
  }

  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: Boolean) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed.toString())
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

  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  public fun disableTimeToLive(disableTimeToLive: String) {
    it.property("disableTimeToLive", disableTimeToLive)
  }

  public fun disableTimeToLive(disableTimeToLive: Boolean) {
    it.property("disableTimeToLive", disableTimeToLive.toString())
  }

  public fun explicitQosEnabled(explicitQosEnabled: String) {
    it.property("explicitQosEnabled", explicitQosEnabled)
  }

  public fun explicitQosEnabled(explicitQosEnabled: Boolean) {
    it.property("explicitQosEnabled", explicitQosEnabled.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun preserveMessageQos(preserveMessageQos: String) {
    it.property("preserveMessageQos", preserveMessageQos)
  }

  public fun preserveMessageQos(preserveMessageQos: Boolean) {
    it.property("preserveMessageQos", preserveMessageQos.toString())
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

  public fun destinationCreationStrategy(destinationCreationStrategy: String) {
    it.property("destinationCreationStrategy", destinationCreationStrategy)
  }

  public fun exceptionListener(exceptionListener: String) {
    it.property("exceptionListener", exceptionListener)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
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

  public fun messageCreatedStrategy(messageCreatedStrategy: String) {
    it.property("messageCreatedStrategy", messageCreatedStrategy)
  }

  public fun recoveryInterval(recoveryInterval: String) {
    it.property("recoveryInterval", recoveryInterval)
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

  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }
}
