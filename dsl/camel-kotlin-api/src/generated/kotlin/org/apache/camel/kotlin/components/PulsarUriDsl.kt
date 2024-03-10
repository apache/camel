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
 * Send and receive messages from/to Apache Pulsar messaging system.
 */
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

  /**
   * Whether the topic is persistent or non-persistent
   */
  public fun persistence(persistence: String) {
    this.persistence = persistence
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  /**
   * The tenant
   */
  public fun tenant(tenant: String) {
    this.tenant = tenant
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  /**
   * The namespace
   */
  public fun namespace(namespace: String) {
    this.namespace = namespace
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  /**
   * The topic
   */
  public fun topic(topic: String) {
    this.topic = topic
    it.url("$persistence://$tenant/$namespace/$topic")
  }

  /**
   * The Authentication FQCN to be used while creating the client from URI
   */
  public fun authenticationClass(authenticationClass: String) {
    it.property("authenticationClass", authenticationClass)
  }

  /**
   * The Authentication Parameters to be used while creating the client from URI
   */
  public fun authenticationParams(authenticationParams: String) {
    it.property("authenticationParams", authenticationParams)
  }

  /**
   * The Pulsar Service URL to point while creating the client from URI
   */
  public fun serviceUrl(serviceUrl: String) {
    it.property("serviceUrl", serviceUrl)
  }

  /**
   * Group the consumer acknowledgments for the specified time in milliseconds - defaults to 100
   */
  public fun ackGroupTimeMillis(ackGroupTimeMillis: String) {
    it.property("ackGroupTimeMillis", ackGroupTimeMillis)
  }

  /**
   * Group the consumer acknowledgments for the specified time in milliseconds - defaults to 100
   */
  public fun ackGroupTimeMillis(ackGroupTimeMillis: Int) {
    it.property("ackGroupTimeMillis", ackGroupTimeMillis.toString())
  }

  /**
   * Timeout for unacknowledged messages in milliseconds - defaults to 10000
   */
  public fun ackTimeoutMillis(ackTimeoutMillis: String) {
    it.property("ackTimeoutMillis", ackTimeoutMillis)
  }

  /**
   * Timeout for unacknowledged messages in milliseconds - defaults to 10000
   */
  public fun ackTimeoutMillis(ackTimeoutMillis: Int) {
    it.property("ackTimeoutMillis", ackTimeoutMillis.toString())
  }

  /**
   * RedeliveryBackoff to use for ack timeout redelivery backoff.
   */
  public fun ackTimeoutRedeliveryBackoff(ackTimeoutRedeliveryBackoff: String) {
    it.property("ackTimeoutRedeliveryBackoff", ackTimeoutRedeliveryBackoff)
  }

  /**
   * Whether to allow manual message acknowledgements. If this option is enabled, then messages are
   * not acknowledged automatically after successful route completion. Instead, an instance of
   * PulsarMessageReceipt is stored as a header on the org.apache.camel.Exchange. Messages can then be
   * acknowledged using PulsarMessageReceipt at any time before the ackTimeout occurs.
   */
  public fun allowManualAcknowledgement(allowManualAcknowledgement: String) {
    it.property("allowManualAcknowledgement", allowManualAcknowledgement)
  }

  /**
   * Whether to allow manual message acknowledgements. If this option is enabled, then messages are
   * not acknowledged automatically after successful route completion. Instead, an instance of
   * PulsarMessageReceipt is stored as a header on the org.apache.camel.Exchange. Messages can then be
   * acknowledged using PulsarMessageReceipt at any time before the ackTimeout occurs.
   */
  public fun allowManualAcknowledgement(allowManualAcknowledgement: Boolean) {
    it.property("allowManualAcknowledgement", allowManualAcknowledgement.toString())
  }

  /**
   * Name of the consumer when subscription is EXCLUSIVE
   */
  public fun consumerName(consumerName: String) {
    it.property("consumerName", consumerName)
  }

  /**
   * Prefix to add to consumer names when a SHARED or FAILOVER subscription is used
   */
  public fun consumerNamePrefix(consumerNamePrefix: String) {
    it.property("consumerNamePrefix", consumerNamePrefix)
  }

  /**
   * Size of the consumer queue - defaults to 10
   */
  public fun consumerQueueSize(consumerQueueSize: String) {
    it.property("consumerQueueSize", consumerQueueSize)
  }

  /**
   * Size of the consumer queue - defaults to 10
   */
  public fun consumerQueueSize(consumerQueueSize: Int) {
    it.property("consumerQueueSize", consumerQueueSize.toString())
  }

  /**
   * Name of the topic where the messages which fail maxRedeliverCount times will be sent. Note: if
   * not set, default topic name will be topicName-subscriptionName-DLQ
   */
  public fun deadLetterTopic(deadLetterTopic: String) {
    it.property("deadLetterTopic", deadLetterTopic)
  }

  /**
   * To enable retry letter topic mode. The default retry letter topic uses this format:
   * topicname-subscriptionname-RETRY
   */
  public fun enableRetry(enableRetry: String) {
    it.property("enableRetry", enableRetry)
  }

  /**
   * To enable retry letter topic mode. The default retry letter topic uses this format:
   * topicname-subscriptionname-RETRY
   */
  public fun enableRetry(enableRetry: Boolean) {
    it.property("enableRetry", enableRetry.toString())
  }

  /**
   * Policy to use by consumer when using key-shared subscription type.
   */
  public fun keySharedPolicy(keySharedPolicy: String) {
    it.property("keySharedPolicy", keySharedPolicy)
  }

  /**
   * Maximum number of times that a message will be redelivered before being sent to the dead letter
   * queue. If this value is not set, no Dead Letter Policy will be created
   */
  public fun maxRedeliverCount(maxRedeliverCount: String) {
    it.property("maxRedeliverCount", maxRedeliverCount)
  }

  /**
   * Maximum number of times that a message will be redelivered before being sent to the dead letter
   * queue. If this value is not set, no Dead Letter Policy will be created
   */
  public fun maxRedeliverCount(maxRedeliverCount: Int) {
    it.property("maxRedeliverCount", maxRedeliverCount.toString())
  }

  /**
   * Whether to use the messageListener interface, or to receive messages using a separate thread
   * pool
   */
  public fun messageListener(messageListener: String) {
    it.property("messageListener", messageListener)
  }

  /**
   * Whether to use the messageListener interface, or to receive messages using a separate thread
   * pool
   */
  public fun messageListener(messageListener: Boolean) {
    it.property("messageListener", messageListener.toString())
  }

  /**
   * RedeliveryBackoff to use for negative ack redelivery backoff.
   */
  public fun negativeAckRedeliveryBackoff(negativeAckRedeliveryBackoff: String) {
    it.property("negativeAckRedeliveryBackoff", negativeAckRedeliveryBackoff)
  }

  /**
   * Set the negative acknowledgement delay
   */
  public fun negativeAckRedeliveryDelayMicros(negativeAckRedeliveryDelayMicros: String) {
    it.property("negativeAckRedeliveryDelayMicros", negativeAckRedeliveryDelayMicros)
  }

  /**
   * Set the negative acknowledgement delay
   */
  public fun negativeAckRedeliveryDelayMicros(negativeAckRedeliveryDelayMicros: Int) {
    it.property("negativeAckRedeliveryDelayMicros", negativeAckRedeliveryDelayMicros.toString())
  }

  /**
   * Number of consumers - defaults to 1
   */
  public fun numberOfConsumers(numberOfConsumers: String) {
    it.property("numberOfConsumers", numberOfConsumers)
  }

  /**
   * Number of consumers - defaults to 1
   */
  public fun numberOfConsumers(numberOfConsumers: Int) {
    it.property("numberOfConsumers", numberOfConsumers.toString())
  }

  /**
   * Number of threads to receive and handle messages when using a separate thread pool
   */
  public fun numberOfConsumerThreads(numberOfConsumerThreads: String) {
    it.property("numberOfConsumerThreads", numberOfConsumerThreads)
  }

  /**
   * Number of threads to receive and handle messages when using a separate thread pool
   */
  public fun numberOfConsumerThreads(numberOfConsumerThreads: Int) {
    it.property("numberOfConsumerThreads", numberOfConsumerThreads.toString())
  }

  /**
   * Enable compacted topic reading.
   */
  public fun readCompacted(readCompacted: String) {
    it.property("readCompacted", readCompacted)
  }

  /**
   * Enable compacted topic reading.
   */
  public fun readCompacted(readCompacted: Boolean) {
    it.property("readCompacted", readCompacted.toString())
  }

  /**
   * Name of the topic to use in retry mode. Note: if not set, default topic name will be
   * topicName-subscriptionName-RETRY
   */
  public fun retryLetterTopic(retryLetterTopic: String) {
    it.property("retryLetterTopic", retryLetterTopic)
  }

  /**
   * Control the initial position in the topic of a newly created subscription. Default is latest
   * message.
   */
  public fun subscriptionInitialPosition(subscriptionInitialPosition: String) {
    it.property("subscriptionInitialPosition", subscriptionInitialPosition)
  }

  /**
   * Name of the subscription to use
   */
  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
  }

  /**
   * Determines to which topics this consumer should be subscribed to - Persistent, Non-Persistent,
   * or both. Only used with pattern subscriptions.
   */
  public fun subscriptionTopicsMode(subscriptionTopicsMode: String) {
    it.property("subscriptionTopicsMode", subscriptionTopicsMode)
  }

  /**
   * Type of the subscription EXCLUSIVESHAREDFAILOVERKEY_SHARED, defaults to EXCLUSIVE
   */
  public fun subscriptionType(subscriptionType: String) {
    it.property("subscriptionType", subscriptionType)
  }

  /**
   * Whether the topic is a pattern (regular expression) that allows the consumer to subscribe to
   * all matching topics in the namespace
   */
  public fun topicsPattern(topicsPattern: String) {
    it.property("topicsPattern", topicsPattern)
  }

  /**
   * Whether the topic is a pattern (regular expression) that allows the consumer to subscribe to
   * all matching topics in the namespace
   */
  public fun topicsPattern(topicsPattern: Boolean) {
    it.property("topicsPattern", topicsPattern.toString())
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
   * Control batching method used by the producer.
   */
  public fun batcherBuilder(batcherBuilder: String) {
    it.property("batcherBuilder", batcherBuilder)
  }

  /**
   * Control whether automatic batching of messages is enabled for the producer.
   */
  public fun batchingEnabled(batchingEnabled: String) {
    it.property("batchingEnabled", batchingEnabled)
  }

  /**
   * Control whether automatic batching of messages is enabled for the producer.
   */
  public fun batchingEnabled(batchingEnabled: Boolean) {
    it.property("batchingEnabled", batchingEnabled.toString())
  }

  /**
   * The maximum size to batch messages.
   */
  public fun batchingMaxMessages(batchingMaxMessages: String) {
    it.property("batchingMaxMessages", batchingMaxMessages)
  }

  /**
   * The maximum size to batch messages.
   */
  public fun batchingMaxMessages(batchingMaxMessages: Int) {
    it.property("batchingMaxMessages", batchingMaxMessages.toString())
  }

  /**
   * The maximum time period within which the messages sent will be batched if batchingEnabled is
   * true.
   */
  public fun batchingMaxPublishDelayMicros(batchingMaxPublishDelayMicros: String) {
    it.property("batchingMaxPublishDelayMicros", batchingMaxPublishDelayMicros)
  }

  /**
   * The maximum time period within which the messages sent will be batched if batchingEnabled is
   * true.
   */
  public fun batchingMaxPublishDelayMicros(batchingMaxPublishDelayMicros: Int) {
    it.property("batchingMaxPublishDelayMicros", batchingMaxPublishDelayMicros.toString())
  }

  /**
   * Whether to block the producing thread if pending messages queue is full or to throw a
   * ProducerQueueIsFullError
   */
  public fun blockIfQueueFull(blockIfQueueFull: String) {
    it.property("blockIfQueueFull", blockIfQueueFull)
  }

  /**
   * Whether to block the producing thread if pending messages queue is full or to throw a
   * ProducerQueueIsFullError
   */
  public fun blockIfQueueFull(blockIfQueueFull: Boolean) {
    it.property("blockIfQueueFull", blockIfQueueFull.toString())
  }

  /**
   * Control whether chunking of messages is enabled for the producer.
   */
  public fun chunkingEnabled(chunkingEnabled: String) {
    it.property("chunkingEnabled", chunkingEnabled)
  }

  /**
   * Control whether chunking of messages is enabled for the producer.
   */
  public fun chunkingEnabled(chunkingEnabled: Boolean) {
    it.property("chunkingEnabled", chunkingEnabled.toString())
  }

  /**
   * Compression type to use
   */
  public fun compressionType(compressionType: String) {
    it.property("compressionType", compressionType)
  }

  /**
   * Hashing function to use when choosing the partition to use for a particular message
   */
  public fun hashingScheme(hashingScheme: String) {
    it.property("hashingScheme", hashingScheme)
  }

  /**
   * The first message published will have a sequence Id of initialSequenceId 1.
   */
  public fun initialSequenceId(initialSequenceId: String) {
    it.property("initialSequenceId", initialSequenceId)
  }

  /**
   * The first message published will have a sequence Id of initialSequenceId 1.
   */
  public fun initialSequenceId(initialSequenceId: Int) {
    it.property("initialSequenceId", initialSequenceId.toString())
  }

  /**
   * Size of the pending massages queue. When the queue is full, by default, any further sends will
   * fail unless blockIfQueueFull=true
   */
  public fun maxPendingMessages(maxPendingMessages: String) {
    it.property("maxPendingMessages", maxPendingMessages)
  }

  /**
   * Size of the pending massages queue. When the queue is full, by default, any further sends will
   * fail unless blockIfQueueFull=true
   */
  public fun maxPendingMessages(maxPendingMessages: Int) {
    it.property("maxPendingMessages", maxPendingMessages.toString())
  }

  /**
   * The maximum number of pending messages for partitioned topics. The maxPendingMessages value
   * will be reduced if (number of partitions maxPendingMessages) exceeds this value. Partitioned
   * topics have a pending message queue for each partition.
   */
  public fun maxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions: String) {
    it.property("maxPendingMessagesAcrossPartitions", maxPendingMessagesAcrossPartitions)
  }

  /**
   * The maximum number of pending messages for partitioned topics. The maxPendingMessages value
   * will be reduced if (number of partitions maxPendingMessages) exceeds this value. Partitioned
   * topics have a pending message queue for each partition.
   */
  public fun maxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions: Int) {
    it.property("maxPendingMessagesAcrossPartitions", maxPendingMessagesAcrossPartitions.toString())
  }

  /**
   * Custom Message Router to use
   */
  public fun messageRouter(messageRouter: String) {
    it.property("messageRouter", messageRouter)
  }

  /**
   * Message Routing Mode to use
   */
  public fun messageRoutingMode(messageRoutingMode: String) {
    it.property("messageRoutingMode", messageRoutingMode)
  }

  /**
   * Name of the producer. If unset, lets Pulsar select a unique identifier.
   */
  public fun producerName(producerName: String) {
    it.property("producerName", producerName)
  }

  /**
   * Send timeout in milliseconds
   */
  public fun sendTimeoutMs(sendTimeoutMs: String) {
    it.property("sendTimeoutMs", sendTimeoutMs)
  }

  /**
   * Send timeout in milliseconds
   */
  public fun sendTimeoutMs(sendTimeoutMs: Int) {
    it.property("sendTimeoutMs", sendTimeoutMs.toString())
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
}
