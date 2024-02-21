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

public fun UriDsl.pulsar(i: PulsarUriDsl.() -> Unit) {
  PulsarUriDsl(this).apply(i)
}

@CamelDslMarker
public class PulsarUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("pulsar")
  }

  private var persistence: String = ""

  private var tenant: String = ""

  private var namespace: String = ""

  private var topic: String = ""

  public fun persistence(persistence: String) {
    this.persistence = persistence
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  public fun tenant(tenant: String) {
    this.tenant = tenant
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  public fun namespace(namespace: String) {
    this.namespace = namespace
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  public fun topic(topic: String) {
    this.topic = topic
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  public fun authenticationClass(authenticationClass: String) {
    it.property("authenticationClass", authenticationClass)
  }

  public fun authenticationParams(authenticationParams: String) {
    it.property("authenticationParams", authenticationParams)
  }

  public fun serviceUrl(serviceUrl: String) {
    it.property("serviceUrl", serviceUrl)
  }

  public fun ackGroupTimeMillis(ackGroupTimeMillis: String) {
    it.property("ackGroupTimeMillis", ackGroupTimeMillis)
  }

  public fun ackGroupTimeMillis(ackGroupTimeMillis: Int) {
    it.property("ackGroupTimeMillis", ackGroupTimeMillis.toString())
  }

  public fun ackTimeoutMillis(ackTimeoutMillis: String) {
    it.property("ackTimeoutMillis", ackTimeoutMillis)
  }

  public fun ackTimeoutMillis(ackTimeoutMillis: Int) {
    it.property("ackTimeoutMillis", ackTimeoutMillis.toString())
  }

  public fun ackTimeoutRedeliveryBackoff(ackTimeoutRedeliveryBackoff: String) {
    it.property("ackTimeoutRedeliveryBackoff", ackTimeoutRedeliveryBackoff)
  }

  public fun allowManualAcknowledgement(allowManualAcknowledgement: String) {
    it.property("allowManualAcknowledgement", allowManualAcknowledgement)
  }

  public fun allowManualAcknowledgement(allowManualAcknowledgement: Boolean) {
    it.property("allowManualAcknowledgement", allowManualAcknowledgement.toString())
  }

  public fun consumerName(consumerName: String) {
    it.property("consumerName", consumerName)
  }

  public fun consumerNamePrefix(consumerNamePrefix: String) {
    it.property("consumerNamePrefix", consumerNamePrefix)
  }

  public fun consumerQueueSize(consumerQueueSize: String) {
    it.property("consumerQueueSize", consumerQueueSize)
  }

  public fun consumerQueueSize(consumerQueueSize: Int) {
    it.property("consumerQueueSize", consumerQueueSize.toString())
  }

  public fun deadLetterTopic(deadLetterTopic: String) {
    it.property("deadLetterTopic", deadLetterTopic)
  }

  public fun enableRetry(enableRetry: String) {
    it.property("enableRetry", enableRetry)
  }

  public fun enableRetry(enableRetry: Boolean) {
    it.property("enableRetry", enableRetry.toString())
  }

  public fun keySharedPolicy(keySharedPolicy: String) {
    it.property("keySharedPolicy", keySharedPolicy)
  }

  public fun maxRedeliverCount(maxRedeliverCount: String) {
    it.property("maxRedeliverCount", maxRedeliverCount)
  }

  public fun maxRedeliverCount(maxRedeliverCount: Int) {
    it.property("maxRedeliverCount", maxRedeliverCount.toString())
  }

  public fun messageListener(messageListener: String) {
    it.property("messageListener", messageListener)
  }

  public fun messageListener(messageListener: Boolean) {
    it.property("messageListener", messageListener.toString())
  }

  public fun negativeAckRedeliveryBackoff(negativeAckRedeliveryBackoff: String) {
    it.property("negativeAckRedeliveryBackoff", negativeAckRedeliveryBackoff)
  }

  public fun negativeAckRedeliveryDelayMicros(negativeAckRedeliveryDelayMicros: String) {
    it.property("negativeAckRedeliveryDelayMicros", negativeAckRedeliveryDelayMicros)
  }

  public fun negativeAckRedeliveryDelayMicros(negativeAckRedeliveryDelayMicros: Int) {
    it.property("negativeAckRedeliveryDelayMicros", negativeAckRedeliveryDelayMicros.toString())
  }

  public fun numberOfConsumers(numberOfConsumers: String) {
    it.property("numberOfConsumers", numberOfConsumers)
  }

  public fun numberOfConsumers(numberOfConsumers: Int) {
    it.property("numberOfConsumers", numberOfConsumers.toString())
  }

  public fun numberOfConsumerThreads(numberOfConsumerThreads: String) {
    it.property("numberOfConsumerThreads", numberOfConsumerThreads)
  }

  public fun numberOfConsumerThreads(numberOfConsumerThreads: Int) {
    it.property("numberOfConsumerThreads", numberOfConsumerThreads.toString())
  }

  public fun readCompacted(readCompacted: String) {
    it.property("readCompacted", readCompacted)
  }

  public fun readCompacted(readCompacted: Boolean) {
    it.property("readCompacted", readCompacted.toString())
  }

  public fun retryLetterTopic(retryLetterTopic: String) {
    it.property("retryLetterTopic", retryLetterTopic)
  }

  public fun subscriptionInitialPosition(subscriptionInitialPosition: String) {
    it.property("subscriptionInitialPosition", subscriptionInitialPosition)
  }

  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
  }

  public fun subscriptionTopicsMode(subscriptionTopicsMode: String) {
    it.property("subscriptionTopicsMode", subscriptionTopicsMode)
  }

  public fun subscriptionType(subscriptionType: String) {
    it.property("subscriptionType", subscriptionType)
  }

  public fun topicsPattern(topicsPattern: String) {
    it.property("topicsPattern", topicsPattern)
  }

  public fun topicsPattern(topicsPattern: Boolean) {
    it.property("topicsPattern", topicsPattern.toString())
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

  public fun batcherBuilder(batcherBuilder: String) {
    it.property("batcherBuilder", batcherBuilder)
  }

  public fun batchingEnabled(batchingEnabled: String) {
    it.property("batchingEnabled", batchingEnabled)
  }

  public fun batchingEnabled(batchingEnabled: Boolean) {
    it.property("batchingEnabled", batchingEnabled.toString())
  }

  public fun batchingMaxMessages(batchingMaxMessages: String) {
    it.property("batchingMaxMessages", batchingMaxMessages)
  }

  public fun batchingMaxMessages(batchingMaxMessages: Int) {
    it.property("batchingMaxMessages", batchingMaxMessages.toString())
  }

  public fun batchingMaxPublishDelayMicros(batchingMaxPublishDelayMicros: String) {
    it.property("batchingMaxPublishDelayMicros", batchingMaxPublishDelayMicros)
  }

  public fun batchingMaxPublishDelayMicros(batchingMaxPublishDelayMicros: Int) {
    it.property("batchingMaxPublishDelayMicros", batchingMaxPublishDelayMicros.toString())
  }

  public fun blockIfQueueFull(blockIfQueueFull: String) {
    it.property("blockIfQueueFull", blockIfQueueFull)
  }

  public fun blockIfQueueFull(blockIfQueueFull: Boolean) {
    it.property("blockIfQueueFull", blockIfQueueFull.toString())
  }

  public fun chunkingEnabled(chunkingEnabled: String) {
    it.property("chunkingEnabled", chunkingEnabled)
  }

  public fun chunkingEnabled(chunkingEnabled: Boolean) {
    it.property("chunkingEnabled", chunkingEnabled.toString())
  }

  public fun compressionType(compressionType: String) {
    it.property("compressionType", compressionType)
  }

  public fun hashingScheme(hashingScheme: String) {
    it.property("hashingScheme", hashingScheme)
  }

  public fun initialSequenceId(initialSequenceId: String) {
    it.property("initialSequenceId", initialSequenceId)
  }

  public fun initialSequenceId(initialSequenceId: Int) {
    it.property("initialSequenceId", initialSequenceId.toString())
  }

  public fun maxPendingMessages(maxPendingMessages: String) {
    it.property("maxPendingMessages", maxPendingMessages)
  }

  public fun maxPendingMessages(maxPendingMessages: Int) {
    it.property("maxPendingMessages", maxPendingMessages.toString())
  }

  public fun maxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions: String) {
    it.property("maxPendingMessagesAcrossPartitions", maxPendingMessagesAcrossPartitions)
  }

  public fun maxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions: Int) {
    it.property("maxPendingMessagesAcrossPartitions", maxPendingMessagesAcrossPartitions.toString())
  }

  public fun messageRouter(messageRouter: String) {
    it.property("messageRouter", messageRouter)
  }

  public fun messageRoutingMode(messageRoutingMode: String) {
    it.property("messageRoutingMode", messageRoutingMode)
  }

  public fun producerName(producerName: String) {
    it.property("producerName", producerName)
  }

  public fun sendTimeoutMs(sendTimeoutMs: String) {
    it.property("sendTimeoutMs", sendTimeoutMs)
  }

  public fun sendTimeoutMs(sendTimeoutMs: Int) {
    it.property("sendTimeoutMs", sendTimeoutMs.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
