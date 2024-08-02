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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Sent and receive messages to/from an Apache Kafka broker.
 */
public fun UriDsl.kafka(i: KafkaUriDsl.() -> Unit) {
  KafkaUriDsl(this).apply(i)
}

@CamelDslMarker
public class KafkaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("kafka")
  }

  private var topic: String = ""

  /**
   * Name of the topic to use. On the consumer you can use comma to separate multiple topics. A
   * producer can only send a message to a single topic.
   */
  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  /**
   * Sets additional properties for either kafka consumer or kafka producer in case they can't be
   * set directly on the camel configurations (e.g.: new Kafka properties that are not reflected yet in
   * Camel configurations), the properties have to be prefixed with additionalProperties.., e.g.:
   * additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro
   */
  public fun additionalProperties(additionalProperties: String) {
    it.property("additionalProperties", additionalProperties)
  }

  /**
   * URL of the Kafka brokers to use. The format is host1:port1,host2:port2, and the list can be a
   * subset of brokers or a VIP pointing to a subset of brokers. This option is known as
   * bootstrap.servers in the Kafka documentation.
   */
  public fun brokers(brokers: String) {
    it.property("brokers", brokers)
  }

  /**
   * The client id is a user-specified string sent in each request to help trace calls. It should
   * logically identify the application making the request.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * The maximum amount of time in milliseconds to wait when reconnecting to a broker that has
   * repeatedly failed to connect. If provided, the backoff per host will increase exponentially for
   * each consecutive connection failure, up to this maximum. After calculating the backoff increase,
   * 20% random jitter is added to avoid connection storms.
   */
  public fun reconnectBackoffMaxMs(reconnectBackoffMaxMs: String) {
    it.property("reconnectBackoffMaxMs", reconnectBackoffMaxMs)
  }

  /**
   * The maximum amount of time in milliseconds to wait when reconnecting to a broker that has
   * repeatedly failed to connect. If provided, the backoff per host will increase exponentially for
   * each consecutive connection failure, up to this maximum. After calculating the backoff increase,
   * 20% random jitter is added to avoid connection storms.
   */
  public fun reconnectBackoffMaxMs(reconnectBackoffMaxMs: Int) {
    it.property("reconnectBackoffMaxMs", reconnectBackoffMaxMs.toString())
  }

  /**
   * The maximum amount of time in milliseconds to wait when retrying a request to the broker that
   * has repeatedly failed. If provided, the backoff per client will increase exponentially for each
   * failed request, up to this maximum. To prevent all clients from being synchronized upon retry, a
   * randomized jitter with a factor of 0.2 will be applied to the backoff, resulting in the backoff
   * falling within a range between 20% below and 20% above the computed value. If retry.backoff.ms is
   * set to be higher than retry.backoff.max.ms, then retry.backoff.max.ms will be used as a constant
   * backoff from the beginning without any exponential increase
   */
  public fun retryBackoffMaxMs(retryBackoffMaxMs: String) {
    it.property("retryBackoffMaxMs", retryBackoffMaxMs)
  }

  /**
   * The maximum amount of time in milliseconds to wait when retrying a request to the broker that
   * has repeatedly failed. If provided, the backoff per client will increase exponentially for each
   * failed request, up to this maximum. To prevent all clients from being synchronized upon retry, a
   * randomized jitter with a factor of 0.2 will be applied to the backoff, resulting in the backoff
   * falling within a range between 20% below and 20% above the computed value. If retry.backoff.ms is
   * set to be higher than retry.backoff.max.ms, then retry.backoff.max.ms will be used as a constant
   * backoff from the beginning without any exponential increase
   */
  public fun retryBackoffMaxMs(retryBackoffMaxMs: Int) {
    it.property("retryBackoffMaxMs", retryBackoffMaxMs.toString())
  }

  /**
   * The amount of time to wait before attempting to retry a failed request to a given topic
   * partition. This avoids repeatedly sending requests in a tight loop under some failure scenarios.
   * This value is the initial backoff value and will increase exponentially for each failed request,
   * up to the retry.backoff.max.ms value.
   */
  public fun retryBackoffMs(retryBackoffMs: String) {
    it.property("retryBackoffMs", retryBackoffMs)
  }

  /**
   * The amount of time to wait before attempting to retry a failed request to a given topic
   * partition. This avoids repeatedly sending requests in a tight loop under some failure scenarios.
   * This value is the initial backoff value and will increase exponentially for each failed request,
   * up to the retry.backoff.max.ms value.
   */
  public fun retryBackoffMs(retryBackoffMs: Int) {
    it.property("retryBackoffMs", retryBackoffMs.toString())
  }

  /**
   * Timeout in milliseconds to wait gracefully for the consumer or producer to shut down and
   * terminate its worker threads.
   */
  public fun shutdownTimeout(shutdownTimeout: String) {
    it.property("shutdownTimeout", shutdownTimeout)
  }

  /**
   * Timeout in milliseconds to wait gracefully for the consumer or producer to shut down and
   * terminate its worker threads.
   */
  public fun shutdownTimeout(shutdownTimeout: Int) {
    it.property("shutdownTimeout", shutdownTimeout.toString())
  }

  /**
   * Whether to allow doing manual commits via KafkaManualCommit. If this option is enabled then an
   * instance of KafkaManualCommit is stored on the Exchange message header, which allows end users to
   * access this API and perform manual offset commits via the Kafka consumer.
   */
  public fun allowManualCommit(allowManualCommit: String) {
    it.property("allowManualCommit", allowManualCommit)
  }

  /**
   * Whether to allow doing manual commits via KafkaManualCommit. If this option is enabled then an
   * instance of KafkaManualCommit is stored on the Exchange message header, which allows end users to
   * access this API and perform manual offset commits via the Kafka consumer.
   */
  public fun allowManualCommit(allowManualCommit: Boolean) {
    it.property("allowManualCommit", allowManualCommit.toString())
  }

  /**
   * If true, periodically commit to ZooKeeper the offset of messages already fetched by the
   * consumer. This committed offset will be used when the process fails as the position from which the
   * new consumer will begin.
   */
  public fun autoCommitEnable(autoCommitEnable: String) {
    it.property("autoCommitEnable", autoCommitEnable)
  }

  /**
   * If true, periodically commit to ZooKeeper the offset of messages already fetched by the
   * consumer. This committed offset will be used when the process fails as the position from which the
   * new consumer will begin.
   */
  public fun autoCommitEnable(autoCommitEnable: Boolean) {
    it.property("autoCommitEnable", autoCommitEnable.toString())
  }

  /**
   * The frequency in ms that the consumer offsets are committed to zookeeper.
   */
  public fun autoCommitIntervalMs(autoCommitIntervalMs: String) {
    it.property("autoCommitIntervalMs", autoCommitIntervalMs)
  }

  /**
   * The frequency in ms that the consumer offsets are committed to zookeeper.
   */
  public fun autoCommitIntervalMs(autoCommitIntervalMs: Int) {
    it.property("autoCommitIntervalMs", autoCommitIntervalMs.toString())
  }

  /**
   * What to do when there is no initial offset in ZooKeeper or if an offset is out of range:
   * earliest : automatically reset the offset to the earliest offset latest: automatically reset the
   * offset to the latest offset fail: throw exception to the consumer
   */
  public fun autoOffsetReset(autoOffsetReset: String) {
    it.property("autoOffsetReset", autoOffsetReset)
  }

  /**
   * Whether to use batching for processing or streaming. The default is false, which uses streaming
   */
  public fun batching(batching: String) {
    it.property("batching", batching)
  }

  /**
   * Whether to use batching for processing or streaming. The default is false, which uses streaming
   */
  public fun batching(batching: Boolean) {
    it.property("batching", batching.toString())
  }

  /**
   * This options controls what happens when a consumer is processing an exchange and it fails. If
   * the option is false then the consumer continues to the next message and processes it. If the
   * option is true then the consumer breaks out. Using the default NoopCommitManager will cause the
   * consumer to not commit the offset so that the message is re-attempted. The consumer should use the
   * KafkaManualCommit to determine the best way to handle the message. Using either the
   * SyncCommitManager or the AsyncCommitManager, the consumer will seek back to the offset of the
   * message that caused a failure, and then re-attempt to process this message. However, this can lead
   * to endless processing of the same message if it's bound to fail every time, e.g., a poison
   * message. Therefore, it's recommended to deal with that, for example, by using Camel's error
   * handler.
   */
  public fun breakOnFirstError(breakOnFirstError: String) {
    it.property("breakOnFirstError", breakOnFirstError)
  }

  /**
   * This options controls what happens when a consumer is processing an exchange and it fails. If
   * the option is false then the consumer continues to the next message and processes it. If the
   * option is true then the consumer breaks out. Using the default NoopCommitManager will cause the
   * consumer to not commit the offset so that the message is re-attempted. The consumer should use the
   * KafkaManualCommit to determine the best way to handle the message. Using either the
   * SyncCommitManager or the AsyncCommitManager, the consumer will seek back to the offset of the
   * message that caused a failure, and then re-attempt to process this message. However, this can lead
   * to endless processing of the same message if it's bound to fail every time, e.g., a poison
   * message. Therefore, it's recommended to deal with that, for example, by using Camel's error
   * handler.
   */
  public fun breakOnFirstError(breakOnFirstError: Boolean) {
    it.property("breakOnFirstError", breakOnFirstError.toString())
  }

  /**
   * Automatically check the CRC32 of the records consumed. This ensures no on-the-wire or on-disk
   * corruption to the messages occurred. This check adds some overhead, so it may be disabled in cases
   * seeking extreme performance.
   */
  public fun checkCrcs(checkCrcs: String) {
    it.property("checkCrcs", checkCrcs)
  }

  /**
   * Automatically check the CRC32 of the records consumed. This ensures no on-the-wire or on-disk
   * corruption to the messages occurred. This check adds some overhead, so it may be disabled in cases
   * seeking extreme performance.
   */
  public fun checkCrcs(checkCrcs: Boolean) {
    it.property("checkCrcs", checkCrcs.toString())
  }

  /**
   * The maximum time, in milliseconds, that the code will wait for a synchronous commit to complete
   */
  public fun commitTimeoutMs(commitTimeoutMs: String) {
    it.property("commitTimeoutMs", commitTimeoutMs)
  }

  /**
   * The configuration controls the maximum amount of time the client will wait for the response of
   * a request. If the response is not received before the timeout elapsed, the client will resend the
   * request if necessary or fail the request if retries are exhausted.
   */
  public fun consumerRequestTimeoutMs(consumerRequestTimeoutMs: String) {
    it.property("consumerRequestTimeoutMs", consumerRequestTimeoutMs)
  }

  /**
   * The configuration controls the maximum amount of time the client will wait for the response of
   * a request. If the response is not received before the timeout elapsed, the client will resend the
   * request if necessary or fail the request if retries are exhausted.
   */
  public fun consumerRequestTimeoutMs(consumerRequestTimeoutMs: Int) {
    it.property("consumerRequestTimeoutMs", consumerRequestTimeoutMs.toString())
  }

  /**
   * The number of consumers that connect to kafka server. Each consumer is run on a separate thread
   * that retrieves and process the incoming data.
   */
  public fun consumersCount(consumersCount: String) {
    it.property("consumersCount", consumersCount)
  }

  /**
   * The number of consumers that connect to kafka server. Each consumer is run on a separate thread
   * that retrieves and process the incoming data.
   */
  public fun consumersCount(consumersCount: Int) {
    it.property("consumersCount", consumersCount.toString())
  }

  /**
   * The maximum amount of data the server should return for a fetch request. This is not an
   * absolute maximum, if the first message in the first non-empty partition of the fetch is larger
   * than this value, the message will still be returned to ensure that the consumer can make progress.
   * The maximum message size accepted by the broker is defined via message.max.bytes (broker config)
   * or max.message.bytes (topic config). Note that the consumer performs multiple fetches in parallel.
   */
  public fun fetchMaxBytes(fetchMaxBytes: String) {
    it.property("fetchMaxBytes", fetchMaxBytes)
  }

  /**
   * The maximum amount of data the server should return for a fetch request. This is not an
   * absolute maximum, if the first message in the first non-empty partition of the fetch is larger
   * than this value, the message will still be returned to ensure that the consumer can make progress.
   * The maximum message size accepted by the broker is defined via message.max.bytes (broker config)
   * or max.message.bytes (topic config). Note that the consumer performs multiple fetches in parallel.
   */
  public fun fetchMaxBytes(fetchMaxBytes: Int) {
    it.property("fetchMaxBytes", fetchMaxBytes.toString())
  }

  /**
   * The minimum amount of data the server should return for a fetch request. If insufficient data
   * is available, the request will wait for that much data to accumulate before answering the request.
   */
  public fun fetchMinBytes(fetchMinBytes: String) {
    it.property("fetchMinBytes", fetchMinBytes)
  }

  /**
   * The minimum amount of data the server should return for a fetch request. If insufficient data
   * is available, the request will wait for that much data to accumulate before answering the request.
   */
  public fun fetchMinBytes(fetchMinBytes: Int) {
    it.property("fetchMinBytes", fetchMinBytes.toString())
  }

  /**
   * The maximum amount of time the server will block before answering the fetch request if there
   * isn't enough data to immediately satisfy fetch.min.bytes
   */
  public fun fetchWaitMaxMs(fetchWaitMaxMs: String) {
    it.property("fetchWaitMaxMs", fetchWaitMaxMs)
  }

  /**
   * The maximum amount of time the server will block before answering the fetch request if there
   * isn't enough data to immediately satisfy fetch.min.bytes
   */
  public fun fetchWaitMaxMs(fetchWaitMaxMs: Int) {
    it.property("fetchWaitMaxMs", fetchWaitMaxMs.toString())
  }

  /**
   * A string that uniquely identifies the group of consumer processes to which this consumer
   * belongs. By setting the same group id, multiple processes can indicate that they are all part of
   * the same consumer group. This option is required for consumers.
   */
  public fun groupId(groupId: String) {
    it.property("groupId", groupId)
  }

  /**
   * A unique identifier of the consumer instance provided by the end user. Only non-empty strings
   * are permitted. If set, the consumer is treated as a static member, which means that only one
   * instance with this ID is allowed in the consumer group at any time. This can be used in
   * combination with a larger session timeout to avoid group rebalances caused by transient
   * unavailability (e.g., process restarts). If not set, the consumer will join the group as a dynamic
   * member, which is the traditional behavior.
   */
  public fun groupInstanceId(groupInstanceId: String) {
    it.property("groupInstanceId", groupInstanceId)
  }

  /**
   * To use a custom KafkaHeaderDeserializer to deserialize kafka headers values
   */
  public fun headerDeserializer(headerDeserializer: String) {
    it.property("headerDeserializer", headerDeserializer)
  }

  /**
   * The expected time between heartbeats to the consumer coordinator when using Kafka's group
   * management facilities. Heartbeats are used to ensure that the consumer's session stays active and
   * to facilitate rebalancing when new consumers join or leave the group. The value must be set lower
   * than session.timeout.ms, but typically should be set no higher than 1/3 of that value. It can be
   * adjusted even lower to control the expected time for normal rebalances.
   */
  public fun heartbeatIntervalMs(heartbeatIntervalMs: String) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs)
  }

  /**
   * The expected time between heartbeats to the consumer coordinator when using Kafka's group
   * management facilities. Heartbeats are used to ensure that the consumer's session stays active and
   * to facilitate rebalancing when new consumers join or leave the group. The value must be set lower
   * than session.timeout.ms, but typically should be set no higher than 1/3 of that value. It can be
   * adjusted even lower to control the expected time for normal rebalances.
   */
  public fun heartbeatIntervalMs(heartbeatIntervalMs: Int) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs.toString())
  }

  /**
   * Deserializer class for the key that implements the Deserializer interface.
   */
  public fun keyDeserializer(keyDeserializer: String) {
    it.property("keyDeserializer", keyDeserializer)
  }

  /**
   * The maximum amount of data per-partition the server will return. The maximum total memory used
   * for a request will be #partitions max.partition.fetch.bytes. This size must be at least as large
   * as the maximum message size the server allows or else it is possible for the producer to send
   * messages larger than the consumer can fetch. If that happens, the consumer can get stuck trying to
   * fetch a large message on a certain partition.
   */
  public fun maxPartitionFetchBytes(maxPartitionFetchBytes: String) {
    it.property("maxPartitionFetchBytes", maxPartitionFetchBytes)
  }

  /**
   * The maximum amount of data per-partition the server will return. The maximum total memory used
   * for a request will be #partitions max.partition.fetch.bytes. This size must be at least as large
   * as the maximum message size the server allows or else it is possible for the producer to send
   * messages larger than the consumer can fetch. If that happens, the consumer can get stuck trying to
   * fetch a large message on a certain partition.
   */
  public fun maxPartitionFetchBytes(maxPartitionFetchBytes: Int) {
    it.property("maxPartitionFetchBytes", maxPartitionFetchBytes.toString())
  }

  /**
   * The maximum delay between invocations of poll() when using consumer group management. This
   * places an upper bound on the amount of time that the consumer can be idle before fetching more
   * records. If poll() is not called before expiration of this timeout, then the consumer is
   * considered failed, and the group will re-balance to reassign the partitions to another member.
   */
  public fun maxPollIntervalMs(maxPollIntervalMs: String) {
    it.property("maxPollIntervalMs", maxPollIntervalMs)
  }

  /**
   * The maximum number of records returned in a single call to poll()
   */
  public fun maxPollRecords(maxPollRecords: String) {
    it.property("maxPollRecords", maxPollRecords)
  }

  /**
   * The maximum number of records returned in a single call to poll()
   */
  public fun maxPollRecords(maxPollRecords: Int) {
    it.property("maxPollRecords", maxPollRecords.toString())
  }

  /**
   * The offset repository to use to locally store the offset of each partition of the topic.
   * Defining one will disable the autocommit.
   */
  public fun offsetRepository(offsetRepository: String) {
    it.property("offsetRepository", offsetRepository)
  }

  /**
   * The class name of the partition assignment strategy that the client will use to distribute
   * partition ownership amongst consumer instances when group management is used
   */
  public fun partitionAssignor(partitionAssignor: String) {
    it.property("partitionAssignor", partitionAssignor)
  }

  /**
   * What to do if kafka threw an exception while polling for new messages. Will by default use the
   * value from the component configuration unless an explicit value has been configured on the
   * endpoint level. DISCARD will discard the message and continue to poll the next message.
   * ERROR_HANDLER will use Camel's error handler to process the exception, and afterwards continue to
   * poll the next message. RECONNECT will re-connect the consumer and try polling the message again.
   * RETRY will let the consumer retry poll the same message again. STOP will stop the consumer (it has
   * to be manually started/restarted if the consumer should be able to consume messages again)
   */
  public fun pollOnError(pollOnError: String) {
    it.property("pollOnError", pollOnError)
  }

  /**
   * The timeout used when polling the KafkaConsumer.
   */
  public fun pollTimeoutMs(pollTimeoutMs: String) {
    it.property("pollTimeoutMs", pollTimeoutMs)
  }

  /**
   * Whether to eager validate that broker host:port is valid and can be DNS resolved to known host
   * during starting this consumer. If the validation fails, then an exception is thrown, which makes
   * Camel fail fast. Disabling this will postpone the validation after the consumer is started, and
   * Camel will keep re-connecting in case of validation or DNS resolution error.
   */
  public fun preValidateHostAndPort(preValidateHostAndPort: String) {
    it.property("preValidateHostAndPort", preValidateHostAndPort)
  }

  /**
   * Whether to eager validate that broker host:port is valid and can be DNS resolved to known host
   * during starting this consumer. If the validation fails, then an exception is thrown, which makes
   * Camel fail fast. Disabling this will postpone the validation after the consumer is started, and
   * Camel will keep re-connecting in case of validation or DNS resolution error.
   */
  public fun preValidateHostAndPort(preValidateHostAndPort: Boolean) {
    it.property("preValidateHostAndPort", preValidateHostAndPort.toString())
  }

  /**
   * Set if KafkaConsumer should read from the beginning or the end on startup:
   * SeekPolicy.BEGINNING: read from the beginning. SeekPolicy.END: read from the end.
   */
  public fun seekTo(seekTo: String) {
    it.property("seekTo", seekTo)
  }

  /**
   * The timeout used to detect failures when using Kafka's group management facilities.
   */
  public fun sessionTimeoutMs(sessionTimeoutMs: String) {
    it.property("sessionTimeoutMs", sessionTimeoutMs)
  }

  /**
   * The timeout used to detect failures when using Kafka's group management facilities.
   */
  public fun sessionTimeoutMs(sessionTimeoutMs: Int) {
    it.property("sessionTimeoutMs", sessionTimeoutMs.toString())
  }

  /**
   * This enables the use of a specific Avro reader for use with the in multiple Schema registries
   * documentation with Avro Deserializers implementation. This option is only available externally
   * (not standard Apache Kafka)
   */
  public fun specificAvroReader(specificAvroReader: String) {
    it.property("specificAvroReader", specificAvroReader)
  }

  /**
   * This enables the use of a specific Avro reader for use with the in multiple Schema registries
   * documentation with Avro Deserializers implementation. This option is only available externally
   * (not standard Apache Kafka)
   */
  public fun specificAvroReader(specificAvroReader: Boolean) {
    it.property("specificAvroReader", specificAvroReader.toString())
  }

  /**
   * Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic
   * number of topics matching the pattern.
   */
  public fun topicIsPattern(topicIsPattern: String) {
    it.property("topicIsPattern", topicIsPattern)
  }

  /**
   * Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic
   * number of topics matching the pattern.
   */
  public fun topicIsPattern(topicIsPattern: Boolean) {
    it.property("topicIsPattern", topicIsPattern.toString())
  }

  /**
   * Deserializer class for value that implements the Deserializer interface.
   */
  public fun valueDeserializer(valueDeserializer: String) {
    it.property("valueDeserializer", valueDeserializer)
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
   * Controls how to read messages written transactionally. If set to read_committed,
   * consumer.poll() will only return transactional messages which have been committed. If set to
   * read_uncommitted (the default), consumer.poll() will return all messages, even transactional
   * messages which have been aborted. Non-transactional messages will be returned unconditionally in
   * either mode. Messages will always be returned in offset order. Hence, in read_committed mode,
   * consumer.poll() will only return messages up to the last stable offset (LSO), which is the one
   * less than the offset of the first open transaction. In particular, any messages appearing after
   * messages belonging to ongoing transactions will be withheld until the relevant transaction has
   * been completed. As a result, read_committed consumers will not be able to read up to the high
   * watermark when there are in flight transactions. Further, when in read_committed the seekToEnd
   * method will return the LSO
   */
  public fun isolationLevel(isolationLevel: String) {
    it.property("isolationLevel", isolationLevel)
  }

  /**
   * Factory to use for creating KafkaManualCommit instances. This allows to plugin a custom factory
   * to create custom KafkaManualCommit instances in case special logic is needed when doing manual
   * commits that deviates from the default implementation that comes out of the box.
   */
  public fun kafkaManualCommitFactory(kafkaManualCommitFactory: String) {
    it.property("kafkaManualCommitFactory", kafkaManualCommitFactory)
  }

  /**
   * If this feature is enabled and a single element of a batch is an Exchange or Message, the
   * producer will generate individual kafka header values for it by using the batch Message to
   * determine the values. Normal behavior consists of always using the same header values (which are
   * determined by the parent Exchange which contains the Iterable or Iterator).
   */
  public fun batchWithIndividualHeaders(batchWithIndividualHeaders: String) {
    it.property("batchWithIndividualHeaders", batchWithIndividualHeaders)
  }

  /**
   * If this feature is enabled and a single element of a batch is an Exchange or Message, the
   * producer will generate individual kafka header values for it by using the batch Message to
   * determine the values. Normal behavior consists of always using the same header values (which are
   * determined by the parent Exchange which contains the Iterable or Iterator).
   */
  public fun batchWithIndividualHeaders(batchWithIndividualHeaders: Boolean) {
    it.property("batchWithIndividualHeaders", batchWithIndividualHeaders.toString())
  }

  /**
   * The total bytes of memory the producer can use to buffer records waiting to be sent to the
   * server. If records are sent faster than they can be delivered to the server, the producer will
   * either block or throw an exception based on the preference specified by block.on.buffer.full.This
   * setting should correspond roughly to the total memory the producer will use, but is not a hard
   * bound since not all memory the producer uses is used for buffering. Some additional memory will be
   * used for compression (if compression is enabled) as well as for maintaining in-flight requests.
   */
  public fun bufferMemorySize(bufferMemorySize: String) {
    it.property("bufferMemorySize", bufferMemorySize)
  }

  /**
   * The total bytes of memory the producer can use to buffer records waiting to be sent to the
   * server. If records are sent faster than they can be delivered to the server, the producer will
   * either block or throw an exception based on the preference specified by block.on.buffer.full.This
   * setting should correspond roughly to the total memory the producer will use, but is not a hard
   * bound since not all memory the producer uses is used for buffering. Some additional memory will be
   * used for compression (if compression is enabled) as well as for maintaining in-flight requests.
   */
  public fun bufferMemorySize(bufferMemorySize: Int) {
    it.property("bufferMemorySize", bufferMemorySize.toString())
  }

  /**
   * This parameter allows you to specify the compression codec for all data generated by this
   * producer. Valid values are none, gzip, snappy, lz4 and zstd.
   */
  public fun compressionCodec(compressionCodec: String) {
    it.property("compressionCodec", compressionCodec)
  }

  /**
   * Close idle connections after the number of milliseconds specified by this config.
   */
  public fun connectionMaxIdleMs(connectionMaxIdleMs: String) {
    it.property("connectionMaxIdleMs", connectionMaxIdleMs)
  }

  /**
   * Close idle connections after the number of milliseconds specified by this config.
   */
  public fun connectionMaxIdleMs(connectionMaxIdleMs: Int) {
    it.property("connectionMaxIdleMs", connectionMaxIdleMs.toString())
  }

  /**
   * An upper bound on the time to report success or failure after a call to send() returns. This
   * limits the total time that a record will be delayed prior to sending, the time to await
   * acknowledgement from the broker (if expected), and the time allowed for retriable send failures.
   */
  public fun deliveryTimeoutMs(deliveryTimeoutMs: String) {
    it.property("deliveryTimeoutMs", deliveryTimeoutMs)
  }

  /**
   * An upper bound on the time to report success or failure after a call to send() returns. This
   * limits the total time that a record will be delayed prior to sending, the time to await
   * acknowledgement from the broker (if expected), and the time allowed for retriable send failures.
   */
  public fun deliveryTimeoutMs(deliveryTimeoutMs: Int) {
    it.property("deliveryTimeoutMs", deliveryTimeoutMs.toString())
  }

  /**
   * When set to 'true', the producer will ensure that exactly one copy of each message is written
   * in the stream. If 'false', producer retries due to broker failures, etc., may write duplicates of
   * the retried message in the stream. Note that enabling idempotence requires
   * max.in.flight.requests.per.connection to be less than or equal to 5 (with message ordering
   * preserved for any allowable value), retries to be greater than 0, and acks must be 'all'.
   * Idempotence is enabled by default if no conflicting configurations are set. If conflicting
   * configurations are set and idempotence is not explicitly enabled, idempotence is disabled. If
   * idempotence is explicitly enabled and conflicting configurations are set, a ConfigException is
   * thrown.
   */
  public fun enableIdempotence(enableIdempotence: String) {
    it.property("enableIdempotence", enableIdempotence)
  }

  /**
   * When set to 'true', the producer will ensure that exactly one copy of each message is written
   * in the stream. If 'false', producer retries due to broker failures, etc., may write duplicates of
   * the retried message in the stream. Note that enabling idempotence requires
   * max.in.flight.requests.per.connection to be less than or equal to 5 (with message ordering
   * preserved for any allowable value), retries to be greater than 0, and acks must be 'all'.
   * Idempotence is enabled by default if no conflicting configurations are set. If conflicting
   * configurations are set and idempotence is not explicitly enabled, idempotence is disabled. If
   * idempotence is explicitly enabled and conflicting configurations are set, a ConfigException is
   * thrown.
   */
  public fun enableIdempotence(enableIdempotence: Boolean) {
    it.property("enableIdempotence", enableIdempotence.toString())
  }

  /**
   * To use a custom KafkaHeaderSerializer to serialize kafka headers values
   */
  public fun headerSerializer(headerSerializer: String) {
    it.property("headerSerializer", headerSerializer)
  }

  /**
   * The record key (or null if no key is specified). If this option has been configured then it
   * take precedence over header KafkaConstants#KEY
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * The serializer class for keys (defaults to the same as for messages if nothing is given).
   */
  public fun keySerializer(keySerializer: String) {
    it.property("keySerializer", keySerializer)
  }

  /**
   * The producer groups together any records that arrive in between request transmissions into a
   * single, batched, request. Normally, this occurs only under load when records arrive faster than
   * they can be sent out. However, in some circumstances, the client may want to reduce the number of
   * requests even under a moderate load. This setting achieves this by adding a small amount of
   * artificial delay. That is, rather than immediately sending out a record, the producer will wait
   * for up to the given delay to allow other records to be sent so that they can be batched together.
   * This can be thought of as analogous to Nagle's algorithm in TCP. This setting gives the upper
   * bound on the delay for batching: once we get batch.size worth of records for a partition, it will
   * be sent immediately regardless of this setting, however, if we have fewer than this many bytes
   * accumulated for this partition, we will 'linger' for the specified time waiting for more records
   * to show up. This setting defaults to 0 (i.e., no delay). Setting linger.ms=5, for example, would
   * have the effect of reducing the number of requests sent but would add up to 5ms of latency to
   * records sent in the absence of load.
   */
  public fun lingerMs(lingerMs: String) {
    it.property("lingerMs", lingerMs)
  }

  /**
   * The producer groups together any records that arrive in between request transmissions into a
   * single, batched, request. Normally, this occurs only under load when records arrive faster than
   * they can be sent out. However, in some circumstances, the client may want to reduce the number of
   * requests even under a moderate load. This setting achieves this by adding a small amount of
   * artificial delay. That is, rather than immediately sending out a record, the producer will wait
   * for up to the given delay to allow other records to be sent so that they can be batched together.
   * This can be thought of as analogous to Nagle's algorithm in TCP. This setting gives the upper
   * bound on the delay for batching: once we get batch.size worth of records for a partition, it will
   * be sent immediately regardless of this setting, however, if we have fewer than this many bytes
   * accumulated for this partition, we will 'linger' for the specified time waiting for more records
   * to show up. This setting defaults to 0 (i.e., no delay). Setting linger.ms=5, for example, would
   * have the effect of reducing the number of requests sent but would add up to 5ms of latency to
   * records sent in the absence of load.
   */
  public fun lingerMs(lingerMs: Int) {
    it.property("lingerMs", lingerMs.toString())
  }

  /**
   * The configuration controls how long the KafkaProducer's send(), partitionsFor(),
   * initTransactions(), sendOffsetsToTransaction(), commitTransaction() and abortTransaction() methods
   * will block. For send() this timeout bounds the total time waiting for both metadata fetch and
   * buffer allocation (blocking in the user-supplied serializers or partitioner is not counted against
   * this timeout). For partitionsFor() this time out bounds the time spent waiting for metadata if it
   * is unavailable. The transaction-related methods always block, but may time out if the transaction
   * coordinator could not be discovered or did not respond within the timeout.
   */
  public fun maxBlockMs(maxBlockMs: String) {
    it.property("maxBlockMs", maxBlockMs)
  }

  /**
   * The configuration controls how long the KafkaProducer's send(), partitionsFor(),
   * initTransactions(), sendOffsetsToTransaction(), commitTransaction() and abortTransaction() methods
   * will block. For send() this timeout bounds the total time waiting for both metadata fetch and
   * buffer allocation (blocking in the user-supplied serializers or partitioner is not counted against
   * this timeout). For partitionsFor() this time out bounds the time spent waiting for metadata if it
   * is unavailable. The transaction-related methods always block, but may time out if the transaction
   * coordinator could not be discovered or did not respond within the timeout.
   */
  public fun maxBlockMs(maxBlockMs: Int) {
    it.property("maxBlockMs", maxBlockMs.toString())
  }

  /**
   * The maximum number of unacknowledged requests the client will send on a single connection
   * before blocking. Note that if this setting is set to be greater than 1 and there are failed sends,
   * there is a risk of message re-ordering due to retries (i.e., if retries are enabled).
   */
  public fun maxInFlightRequest(maxInFlightRequest: String) {
    it.property("maxInFlightRequest", maxInFlightRequest)
  }

  /**
   * The maximum number of unacknowledged requests the client will send on a single connection
   * before blocking. Note that if this setting is set to be greater than 1 and there are failed sends,
   * there is a risk of message re-ordering due to retries (i.e., if retries are enabled).
   */
  public fun maxInFlightRequest(maxInFlightRequest: Int) {
    it.property("maxInFlightRequest", maxInFlightRequest.toString())
  }

  /**
   * The maximum size of a request. This is also effectively a cap on the maximum record size. Note
   * that the server has its own cap on record size which may be different from this. This setting will
   * limit the number of record batches the producer will send in a single request to avoid sending
   * huge requests.
   */
  public fun maxRequestSize(maxRequestSize: String) {
    it.property("maxRequestSize", maxRequestSize)
  }

  /**
   * The maximum size of a request. This is also effectively a cap on the maximum record size. Note
   * that the server has its own cap on record size which may be different from this. This setting will
   * limit the number of record batches the producer will send in a single request to avoid sending
   * huge requests.
   */
  public fun maxRequestSize(maxRequestSize: Int) {
    it.property("maxRequestSize", maxRequestSize.toString())
  }

  /**
   * The period of time in milliseconds after which we force a refresh of metadata even if we
   * haven't seen any partition leadership changes to proactively discover any new brokers or
   * partitions.
   */
  public fun metadataMaxAgeMs(metadataMaxAgeMs: String) {
    it.property("metadataMaxAgeMs", metadataMaxAgeMs)
  }

  /**
   * The period of time in milliseconds after which we force a refresh of metadata even if we
   * haven't seen any partition leadership changes to proactively discover any new brokers or
   * partitions.
   */
  public fun metadataMaxAgeMs(metadataMaxAgeMs: Int) {
    it.property("metadataMaxAgeMs", metadataMaxAgeMs.toString())
  }

  /**
   * A list of classes to use as metrics reporters. Implementing the MetricReporter interface allows
   * plugging in classes that will be notified of new metric creation. The JmxReporter is always
   * included to register JMX statistics.
   */
  public fun metricReporters(metricReporters: String) {
    it.property("metricReporters", metricReporters)
  }

  /**
   * The window of time a metrics sample is computed over.
   */
  public fun metricsSampleWindowMs(metricsSampleWindowMs: String) {
    it.property("metricsSampleWindowMs", metricsSampleWindowMs)
  }

  /**
   * The window of time a metrics sample is computed over.
   */
  public fun metricsSampleWindowMs(metricsSampleWindowMs: Int) {
    it.property("metricsSampleWindowMs", metricsSampleWindowMs.toString())
  }

  /**
   * The number of samples maintained to compute metrics.
   */
  public fun noOfMetricsSample(noOfMetricsSample: String) {
    it.property("noOfMetricsSample", noOfMetricsSample)
  }

  /**
   * The number of samples maintained to compute metrics.
   */
  public fun noOfMetricsSample(noOfMetricsSample: Int) {
    it.property("noOfMetricsSample", noOfMetricsSample.toString())
  }

  /**
   * The partitioner class for partitioning messages amongst sub-topics. The default partitioner is
   * based on the hash of the key.
   */
  public fun partitioner(partitioner: String) {
    it.property("partitioner", partitioner)
  }

  /**
   * Whether the message keys should be ignored when computing the partition. This setting has
   * effect only when partitioner is not set
   */
  public fun partitionerIgnoreKeys(partitionerIgnoreKeys: String) {
    it.property("partitionerIgnoreKeys", partitionerIgnoreKeys)
  }

  /**
   * Whether the message keys should be ignored when computing the partition. This setting has
   * effect only when partitioner is not set
   */
  public fun partitionerIgnoreKeys(partitionerIgnoreKeys: Boolean) {
    it.property("partitionerIgnoreKeys", partitionerIgnoreKeys.toString())
  }

  /**
   * The partition to which the record will be sent (or null if no partition was specified). If this
   * option has been configured then it take precedence over header KafkaConstants#PARTITION_KEY
   */
  public fun partitionKey(partitionKey: String) {
    it.property("partitionKey", partitionKey)
  }

  /**
   * The partition to which the record will be sent (or null if no partition was specified). If this
   * option has been configured then it take precedence over header KafkaConstants#PARTITION_KEY
   */
  public fun partitionKey(partitionKey: Int) {
    it.property("partitionKey", partitionKey.toString())
  }

  /**
   * The producer will attempt to batch records together into fewer requests whenever multiple
   * records are being sent to the same partition. This helps performance on both the client and the
   * server. This configuration controls the default batch size in bytes. No attempt will be made to
   * batch records larger than this size. Requests sent to brokers will contain multiple batches, one
   * for each partition with data available to be sent. A small batch size will make batching less
   * common and may reduce throughput (a batch size of zero will disable batching entirely). A very
   * large batch size may use memory a bit more wastefully as we will always allocate a buffer of the
   * specified batch size in anticipation of additional records.
   */
  public fun producerBatchSize(producerBatchSize: String) {
    it.property("producerBatchSize", producerBatchSize)
  }

  /**
   * The producer will attempt to batch records together into fewer requests whenever multiple
   * records are being sent to the same partition. This helps performance on both the client and the
   * server. This configuration controls the default batch size in bytes. No attempt will be made to
   * batch records larger than this size. Requests sent to brokers will contain multiple batches, one
   * for each partition with data available to be sent. A small batch size will make batching less
   * common and may reduce throughput (a batch size of zero will disable batching entirely). A very
   * large batch size may use memory a bit more wastefully as we will always allocate a buffer of the
   * specified batch size in anticipation of additional records.
   */
  public fun producerBatchSize(producerBatchSize: Int) {
    it.property("producerBatchSize", producerBatchSize.toString())
  }

  /**
   * The maximum number of unsent messages that can be queued up the producer when using async mode
   * before either the producer must be blocked or data must be dropped.
   */
  public fun queueBufferingMaxMessages(queueBufferingMaxMessages: String) {
    it.property("queueBufferingMaxMessages", queueBufferingMaxMessages)
  }

  /**
   * The maximum number of unsent messages that can be queued up the producer when using async mode
   * before either the producer must be blocked or data must be dropped.
   */
  public fun queueBufferingMaxMessages(queueBufferingMaxMessages: Int) {
    it.property("queueBufferingMaxMessages", queueBufferingMaxMessages.toString())
  }

  /**
   * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data.
   */
  public fun receiveBufferBytes(receiveBufferBytes: String) {
    it.property("receiveBufferBytes", receiveBufferBytes)
  }

  /**
   * The size of the TCP receive buffer (SO_RCVBUF) to use when reading data.
   */
  public fun receiveBufferBytes(receiveBufferBytes: Int) {
    it.property("receiveBufferBytes", receiveBufferBytes.toString())
  }

  /**
   * The amount of time to wait before attempting to reconnect to a given host. This avoids
   * repeatedly connecting to a host in a tight loop. This backoff applies to all requests sent by the
   * consumer to the broker.
   */
  public fun reconnectBackoffMs(reconnectBackoffMs: String) {
    it.property("reconnectBackoffMs", reconnectBackoffMs)
  }

  /**
   * The amount of time to wait before attempting to reconnect to a given host. This avoids
   * repeatedly connecting to a host in a tight loop. This backoff applies to all requests sent by the
   * consumer to the broker.
   */
  public fun reconnectBackoffMs(reconnectBackoffMs: Int) {
    it.property("reconnectBackoffMs", reconnectBackoffMs.toString())
  }

  /**
   * Whether the producer should store the RecordMetadata results from sending to Kafka. The results
   * are stored in a List containing the RecordMetadata metadata's. The list is stored on a header with
   * the key KafkaConstants#KAFKA_RECORDMETA
   */
  public fun recordMetadata(recordMetadata: String) {
    it.property("recordMetadata", recordMetadata)
  }

  /**
   * Whether the producer should store the RecordMetadata results from sending to Kafka. The results
   * are stored in a List containing the RecordMetadata metadata's. The list is stored on a header with
   * the key KafkaConstants#KAFKA_RECORDMETA
   */
  public fun recordMetadata(recordMetadata: Boolean) {
    it.property("recordMetadata", recordMetadata.toString())
  }

  /**
   * The number of acknowledgments the producer requires the leader to have received before
   * considering a request complete. This controls the durability of records that are sent. The
   * following settings are allowed: acks=0 If set to zero, then the producer will not wait for any
   * acknowledgment from the server at all. The record will be immediately added to the socket buffer
   * and considered sent. No guarantee can be made that the server has received the record in this
   * case, and the retry configuration will not take effect (as the client won't generally know of any
   * failures). The offset given back for each record will always be set to -1. acks=1 This will mean
   * the leader will write the record to its local log but will respond without awaiting full
   * acknowledgment from all followers. In this case should the leader fail immediately after
   * acknowledging the record, but before the followers have replicated it, then the record will be
   * lost. acks=all This means the leader will wait for the full set of in-sync replicas to acknowledge
   * the record. This guarantees that the record will not be lost as long as at least one in-sync
   * replica remains alive. This is the strongest available guarantee. This is equivalent to the
   * acks=-1 setting. Note that enabling idempotence requires this config value to be 'all'. If
   * conflicting configurations are set and idempotence is not explicitly enabled, idempotence is
   * disabled.
   */
  public fun requestRequiredAcks(requestRequiredAcks: String) {
    it.property("requestRequiredAcks", requestRequiredAcks)
  }

  /**
   * The amount of time the broker will wait trying to meet the request.required.acks requirement
   * before sending back an error to the client.
   */
  public fun requestTimeoutMs(requestTimeoutMs: String) {
    it.property("requestTimeoutMs", requestTimeoutMs)
  }

  /**
   * The amount of time the broker will wait trying to meet the request.required.acks requirement
   * before sending back an error to the client.
   */
  public fun requestTimeoutMs(requestTimeoutMs: Int) {
    it.property("requestTimeoutMs", requestTimeoutMs.toString())
  }

  /**
   * Setting a value greater than zero will cause the client to resend any record that has failed to
   * be sent due to a potentially transient error. Note that this retry is no different from if the
   * client re-sending the record upon receiving the error. Produce requests will be failed before the
   * number of retries has been exhausted if the timeout configured by delivery.timeout.ms expires
   * first before successful acknowledgement. Users should generally prefer to leave this config unset
   * and instead use delivery.timeout.ms to control retry behavior. Enabling idempotence requires this
   * config value to be greater than 0. If conflicting configurations are set and idempotence is not
   * explicitly enabled, idempotence is disabled. Allowing retries while setting enable.idempotence to
   * false and max.in.flight.requests.per.connection to 1 will potentially change the ordering of
   * records, because if two batches are sent to a single partition, and the first fails and is retried
   * but the second succeeds; then the records in the second batch may appear first.
   */
  public fun retries(retries: String) {
    it.property("retries", retries)
  }

  /**
   * Setting a value greater than zero will cause the client to resend any record that has failed to
   * be sent due to a potentially transient error. Note that this retry is no different from if the
   * client re-sending the record upon receiving the error. Produce requests will be failed before the
   * number of retries has been exhausted if the timeout configured by delivery.timeout.ms expires
   * first before successful acknowledgement. Users should generally prefer to leave this config unset
   * and instead use delivery.timeout.ms to control retry behavior. Enabling idempotence requires this
   * config value to be greater than 0. If conflicting configurations are set and idempotence is not
   * explicitly enabled, idempotence is disabled. Allowing retries while setting enable.idempotence to
   * false and max.in.flight.requests.per.connection to 1 will potentially change the ordering of
   * records, because if two batches are sent to a single partition, and the first fails and is retried
   * but the second succeeds; then the records in the second batch may appear first.
   */
  public fun retries(retries: Int) {
    it.property("retries", retries.toString())
  }

  /**
   * Socket write buffer size
   */
  public fun sendBufferBytes(sendBufferBytes: String) {
    it.property("sendBufferBytes", sendBufferBytes)
  }

  /**
   * Socket write buffer size
   */
  public fun sendBufferBytes(sendBufferBytes: Int) {
    it.property("sendBufferBytes", sendBufferBytes.toString())
  }

  /**
   * Sets whether sending to kafka should send the message body as a single record, or use a
   * java.util.Iterator to send multiple records to kafka (if the message body can be iterated).
   */
  public fun useIterator(useIterator: String) {
    it.property("useIterator", useIterator)
  }

  /**
   * Sets whether sending to kafka should send the message body as a single record, or use a
   * java.util.Iterator to send multiple records to kafka (if the message body can be iterated).
   */
  public fun useIterator(useIterator: Boolean) {
    it.property("useIterator", useIterator.toString())
  }

  /**
   * The serializer class for messages.
   */
  public fun valueSerializer(valueSerializer: String) {
    it.property("valueSerializer", valueSerializer)
  }

  /**
   * To use a custom worker pool for continue routing Exchange after kafka server has acknowledged
   * the message that was sent to it from KafkaProducer using asynchronous non-blocking processing. If
   * using this option, then you must handle the lifecycle of the thread pool to shut the pool down
   * when no longer needed.
   */
  public fun workerPool(workerPool: String) {
    it.property("workerPool", workerPool)
  }

  /**
   * Number of core threads for the worker pool for continue routing Exchange after kafka server has
   * acknowledged the message that was sent to it from KafkaProducer using asynchronous non-blocking
   * processing.
   */
  public fun workerPoolCoreSize(workerPoolCoreSize: String) {
    it.property("workerPoolCoreSize", workerPoolCoreSize)
  }

  /**
   * Number of core threads for the worker pool for continue routing Exchange after kafka server has
   * acknowledged the message that was sent to it from KafkaProducer using asynchronous non-blocking
   * processing.
   */
  public fun workerPoolCoreSize(workerPoolCoreSize: Int) {
    it.property("workerPoolCoreSize", workerPoolCoreSize.toString())
  }

  /**
   * Maximum number of threads for the worker pool for continue routing Exchange after kafka server
   * has acknowledged the message that was sent to it from KafkaProducer using asynchronous
   * non-blocking processing.
   */
  public fun workerPoolMaxSize(workerPoolMaxSize: String) {
    it.property("workerPoolMaxSize", workerPoolMaxSize)
  }

  /**
   * Maximum number of threads for the worker pool for continue routing Exchange after kafka server
   * has acknowledged the message that was sent to it from KafkaProducer using asynchronous
   * non-blocking processing.
   */
  public fun workerPoolMaxSize(workerPoolMaxSize: Int) {
    it.property("workerPoolMaxSize", workerPoolMaxSize.toString())
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
   * Factory to use for creating org.apache.kafka.clients.consumer.KafkaConsumer and
   * org.apache.kafka.clients.producer.KafkaProducer instances. This allows to configure a custom
   * factory to create instances with logic that extends the vanilla Kafka clients.
   */
  public fun kafkaClientFactory(kafkaClientFactory: String) {
    it.property("kafkaClientFactory", kafkaClientFactory)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * Sets interceptors for producer or consumers. Producer interceptors have to be classes
   * implementing org.apache.kafka.clients.producer.ProducerInterceptor Consumer interceptors have to
   * be classes implementing org.apache.kafka.clients.consumer.ConsumerInterceptor Note that if you use
   * Producer interceptor on a consumer it will throw a class cast exception in runtime
   */
  public fun interceptorClasses(interceptorClasses: String) {
    it.property("interceptorClasses", interceptorClasses)
  }

  /**
   * URL of the schema registry servers to use. The format is host1:port1,host2:port2. This is known
   * as schema.registry.url in multiple Schema registries documentation. This option is only available
   * externally (not standard Apache Kafka)
   */
  public fun schemaRegistryURL(schemaRegistryURL: String) {
    it.property("schemaRegistryURL", schemaRegistryURL)
  }

  /**
   * Login thread sleep time between refresh attempts.
   */
  public fun kerberosBeforeReloginMinTime(kerberosBeforeReloginMinTime: String) {
    it.property("kerberosBeforeReloginMinTime", kerberosBeforeReloginMinTime)
  }

  /**
   * Login thread sleep time between refresh attempts.
   */
  public fun kerberosBeforeReloginMinTime(kerberosBeforeReloginMinTime: Int) {
    it.property("kerberosBeforeReloginMinTime", kerberosBeforeReloginMinTime.toString())
  }

  /**
   * Location of the kerberos config file.
   */
  public fun kerberosConfigLocation(kerberosConfigLocation: String) {
    it.property("kerberosConfigLocation", kerberosConfigLocation)
  }

  /**
   * Kerberos kinit command path. Default is /usr/bin/kinit
   */
  public fun kerberosInitCmd(kerberosInitCmd: String) {
    it.property("kerberosInitCmd", kerberosInitCmd)
  }

  /**
   * A list of rules for mapping from principal names to short names (typically operating system
   * usernames). The rules are evaluated in order, and the first rule that matches a principal name is
   * used to map it to a short name. Any later rules in the list are ignored. By default, principal
   * names of the form {username}/{hostname}{REALM} are mapped to {username}. For more details on the
   * format, please see the Security Authorization and ACLs documentation (at the Apache Kafka project
   * website). Multiple values can be separated by comma
   */
  public fun kerberosPrincipalToLocalRules(kerberosPrincipalToLocalRules: String) {
    it.property("kerberosPrincipalToLocalRules", kerberosPrincipalToLocalRules)
  }

  /**
   * Percentage of random jitter added to the renewal time.
   */
  public fun kerberosRenewJitter(kerberosRenewJitter: String) {
    it.property("kerberosRenewJitter", kerberosRenewJitter)
  }

  /**
   * Percentage of random jitter added to the renewal time.
   */
  public fun kerberosRenewJitter(kerberosRenewJitter: Double) {
    it.property("kerberosRenewJitter", kerberosRenewJitter.toString())
  }

  /**
   * Login thread will sleep until the specified window factor of time from last refresh to ticket's
   * expiry has been reached, at which time it will try to renew the ticket.
   */
  public fun kerberosRenewWindowFactor(kerberosRenewWindowFactor: String) {
    it.property("kerberosRenewWindowFactor", kerberosRenewWindowFactor)
  }

  /**
   * Login thread will sleep until the specified window factor of time from last refresh to ticket's
   * expiry has been reached, at which time it will try to renew the ticket.
   */
  public fun kerberosRenewWindowFactor(kerberosRenewWindowFactor: Double) {
    it.property("kerberosRenewWindowFactor", kerberosRenewWindowFactor.toString())
  }

  /**
   * Expose the kafka sasl.jaas.config parameter Example:
   * org.apache.kafka.common.security.plain.PlainLoginModule required username=USERNAME
   * password=PASSWORD;
   */
  public fun saslJaasConfig(saslJaasConfig: String) {
    it.property("saslJaasConfig", saslJaasConfig)
  }

  /**
   * The Kerberos principal name that Kafka runs as. This can be defined either in Kafka's JAAS
   * config or in Kafka's config.
   */
  public fun saslKerberosServiceName(saslKerberosServiceName: String) {
    it.property("saslKerberosServiceName", saslKerberosServiceName)
  }

  /**
   * The Simple Authentication and Security Layer (SASL) Mechanism used. For the valid values see
   * http://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml
   */
  public fun saslMechanism(saslMechanism: String) {
    it.property("saslMechanism", saslMechanism)
  }

  /**
   * Protocol used to communicate with brokers. SASL_PLAINTEXT, PLAINTEXT, SASL_SSL and SSL are
   * supported
   */
  public fun securityProtocol(securityProtocol: String) {
    it.property("securityProtocol", securityProtocol)
  }

  /**
   * A list of cipher suites. This is a named combination of authentication, encryption, MAC and key
   * exchange algorithm used to negotiate the security settings for a network connection using TLS or
   * SSL network protocol. By default, all the available cipher suites are supported.
   */
  public fun sslCipherSuites(sslCipherSuites: String) {
    it.property("sslCipherSuites", sslCipherSuites)
  }

  /**
   * SSL configuration using a Camel SSLContextParameters object. If configured, it's applied before
   * the other SSL endpoint parameters. NOTE: Kafka only supports loading keystore from file locations,
   * so prefix the location with file: in the KeyStoreParameters.resource option.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * The list of protocols enabled for SSL connections. The default is TLSv1.2,TLSv1.3 when running
   * with Java 11 or newer, TLSv1.2 otherwise. With the default value for Java 11, clients and servers
   * will prefer TLSv1.3 if both support it and fallback to TLSv1.2 otherwise (assuming both support at
   * least TLSv1.2). This default should be fine for most cases. Also see the config documentation for
   * SslProtocol.
   */
  public fun sslEnabledProtocols(sslEnabledProtocols: String) {
    it.property("sslEnabledProtocols", sslEnabledProtocols)
  }

  /**
   * The endpoint identification algorithm to validate server hostname using server certificate. Use
   * none or false to disable server hostname verification.
   */
  public fun sslEndpointAlgorithm(sslEndpointAlgorithm: String) {
    it.property("sslEndpointAlgorithm", sslEndpointAlgorithm)
  }

  /**
   * The algorithm used by key manager factory for SSL connections. Default value is the key manager
   * factory algorithm configured for the Java Virtual Machine.
   */
  public fun sslKeymanagerAlgorithm(sslKeymanagerAlgorithm: String) {
    it.property("sslKeymanagerAlgorithm", sslKeymanagerAlgorithm)
  }

  /**
   * The password of the private key in the key store file or the PEM key specified in
   * sslKeystoreKey. This is required for clients only if two-way authentication is configured.
   */
  public fun sslKeyPassword(sslKeyPassword: String) {
    it.property("sslKeyPassword", sslKeyPassword)
  }

  /**
   * The location of the key store file. This is optional for the client and can be used for two-way
   * authentication for the client.
   */
  public fun sslKeystoreLocation(sslKeystoreLocation: String) {
    it.property("sslKeystoreLocation", sslKeystoreLocation)
  }

  /**
   * The store password for the key store file. This is optional for the client and only needed if
   * sslKeystoreLocation is configured. Key store password is not supported for PEM format.
   */
  public fun sslKeystorePassword(sslKeystorePassword: String) {
    it.property("sslKeystorePassword", sslKeystorePassword)
  }

  /**
   * The file format of the key store file. This is optional for the client. The default value is
   * JKS
   */
  public fun sslKeystoreType(sslKeystoreType: String) {
    it.property("sslKeystoreType", sslKeystoreType)
  }

  /**
   * The SSL protocol used to generate the SSLContext. The default is TLSv1.3 when running with Java
   * 11 or newer, TLSv1.2 otherwise. This value should be fine for most use cases. Allowed values in
   * recent JVMs are TLSv1.2 and TLSv1.3. TLS, TLSv1.1, SSL, SSLv2 and SSLv3 may be supported in older
   * JVMs, but their usage is discouraged due to known security vulnerabilities. With the default value
   * for this config and sslEnabledProtocols, clients will downgrade to TLSv1.2 if the server does not
   * support TLSv1.3. If this config is set to TLSv1.2, clients will not use TLSv1.3 even if it is one
   * of the values in sslEnabledProtocols and the server only supports TLSv1.3.
   */
  public fun sslProtocol(sslProtocol: String) {
    it.property("sslProtocol", sslProtocol)
  }

  /**
   * The name of the security provider used for SSL connections. Default value is the default
   * security provider of the JVM.
   */
  public fun sslProvider(sslProvider: String) {
    it.property("sslProvider", sslProvider)
  }

  /**
   * The algorithm used by trust manager factory for SSL connections. Default value is the trust
   * manager factory algorithm configured for the Java Virtual Machine.
   */
  public fun sslTrustmanagerAlgorithm(sslTrustmanagerAlgorithm: String) {
    it.property("sslTrustmanagerAlgorithm", sslTrustmanagerAlgorithm)
  }

  /**
   * The location of the trust store file.
   */
  public fun sslTruststoreLocation(sslTruststoreLocation: String) {
    it.property("sslTruststoreLocation", sslTruststoreLocation)
  }

  /**
   * The password for the trust store file. If a password is not set, trust store file configured
   * will still be used, but integrity checking is disabled. Trust store password is not supported for
   * PEM format.
   */
  public fun sslTruststorePassword(sslTruststorePassword: String) {
    it.property("sslTruststorePassword", sslTruststorePassword)
  }

  /**
   * The file format of the trust store file. The default value is JKS.
   */
  public fun sslTruststoreType(sslTruststoreType: String) {
    it.property("sslTruststoreType", sslTruststoreType)
  }
}
