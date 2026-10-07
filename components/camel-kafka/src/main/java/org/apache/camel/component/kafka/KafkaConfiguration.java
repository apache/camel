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
package org.apache.camel.component.kafka;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.serde.DefaultKafkaHeaderSerializer;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;

@UriParams
public class KafkaConfiguration extends KafkaClientConfiguration {

    // Common configuration properties
    @UriPath(label = "common")
    @Metadata(required = true)
    private String topic;

    @UriParam(label = "consumer")
    private boolean topicIsPattern;
    @UriParam(label = "consumer")
    private String groupId;
    @UriParam(label = "consumer")
    private String groupInstanceId;

    @UriParam(label = "consumer", defaultValue = "1")
    private int consumersCount = 1;

    // interceptor.classes
    @UriParam(label = "common,monitoring")
    private String interceptorClasses;

    // fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "1")
    private Integer fetchMinBytes = 1;
    // fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "52428800")
    private Integer fetchMaxBytes = 50 * 1024 * 1024;
    // heartbeat.interval.ms
    @UriParam(label = "consumer", defaultValue = "3000")
    private Integer heartbeatIntervalMs = 3000;
    // max.partition.fetch.bytes
    @UriParam(label = "consumer", defaultValue = "1048576")
    private Integer maxPartitionFetchBytes = 1048576;
    // session.timeout.ms
    @UriParam(label = "consumer", defaultValue = "45000")
    private Integer sessionTimeoutMs = 45000;
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer maxPollRecords = 500;
    @UriParam(label = "consumer", defaultValue = "5000", javaType = "java.time.Duration")
    private Long pollTimeoutMs = 5000L;
    @UriParam(label = "consumer", javaType = "java.time.Duration")
    private Integer maxPollIntervalMs;
    // auto.offset.reset1
    @UriParam(label = "consumer", defaultValue = "latest")
    private String autoOffsetReset = "latest";
    // partition.assignment.strategy
    @UriParam(label = "consumer", defaultValue = KafkaConstants.PARTITIONER_RANGE_ASSIGNOR)
    private String partitionAssignor = KafkaConstants.PARTITIONER_RANGE_ASSIGNOR;
    // group.protocol
    @UriParam(label = "consumer", defaultValue = "classic", enums = "classic,consumer")
    private String groupProtocol = "classic";
    // group.remote.assignor
    @UriParam(label = "consumer")
    private String groupRemoteAssignor;
    // request.timeout.ms
    @UriParam(label = "consumer", defaultValue = "30000")
    private Integer consumerRequestTimeoutMs = 30000;
    // auto.commit.interval.ms
    @UriParam(label = "consumer", defaultValue = "5000")
    private Integer autoCommitIntervalMs = 5000;
    // check.crcs
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean checkCrcs = true;
    // fetch.max.wait.ms
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer fetchWaitMaxMs = 500;
    @UriParam(label = "consumer", enums = "BEGINNING,END")
    private SeekPolicy seekTo;

    // Consumer configuration properties
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean autoCommitEnable = true;
    @UriParam(label = "consumer")
    private boolean allowManualCommit;
    @UriParam(label = "consumer")
    private boolean breakOnFirstError;
    @UriParam(label = "consumer")
    private StateRepository<String, String> offsetRepository;
    @UriParam(label = "consumer", defaultValue = "ERROR_HANDLER", enums = "DISCARD,ERROR_HANDLER,RECONNECT,RETRY,STOP")
    private PollOnError pollOnError = PollOnError.ERROR_HANDLER;
    @UriParam(label = "consumer", defaultValue = "5000", javaType = "java.time.Duration")
    private Long commitTimeoutMs = 5000L;
    @UriParam(label = "consumer,advanced", defaultValue = "read_uncommitted", enums = "read_uncommitted,read_committed")
    private String isolationLevel;

    // Producer configuration properties
    @UriParam(label = "producer")
    private String partitioner;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean partitionerIgnoreKeys;

    @UriParam(label = "producer")
    private ExecutorService workerPool;
    @UriParam(label = "producer", defaultValue = "10")
    private Integer workerPoolCoreSize = 10;
    @UriParam(label = "producer", defaultValue = "20")
    private Integer workerPoolMaxSize = 20;

    // Async producer config
    @Deprecated
    @UriParam(label = "producer", defaultValue = "10000", description = "Deprecated: this option has no effect."
                                                                        + " Use bufferMemorySize or maxBlockMs instead.")
    private Integer queueBufferingMaxMessages = 10000;
    @UriParam(label = "producer", defaultValue = KafkaConstants.KAFKA_DEFAULT_SERIALIZER)
    private String valueSerializer = KafkaConstants.KAFKA_DEFAULT_SERIALIZER;
    @UriParam(label = "producer", defaultValue = KafkaConstants.KAFKA_DEFAULT_SERIALIZER)
    private String keySerializer = KafkaConstants.KAFKA_DEFAULT_SERIALIZER;

    @UriParam(label = "producer")
    private String key;
    @UriParam(label = "producer")
    private Integer partitionKey;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean useIterator = true;
    @UriParam(label = "producer", enums = "all,-1,0,1", defaultValue = "all")
    private String requestRequiredAcks = "all";
    // buffer.memory
    @UriParam(label = "producer", defaultValue = "33554432")
    private Integer bufferMemorySize = 33554432;
    // compression.type
    @UriParam(label = "producer", defaultValue = "none", enums = "none,gzip,snappy,lz4,zstd")
    private String compressionCodec = "none";
    // retries
    @UriParam(label = "producer")
    private Integer retries;
    // use individual headers if exchange.body contains Iterable or similar of Message or Exchange
    @UriParam(label = "producer", defaultValue = "false")
    private boolean batchWithIndividualHeaders;
    // batch.size
    @UriParam(label = "producer", defaultValue = "16384")
    private Integer producerBatchSize = 16384;
    // linger.ms
    @UriParam(label = "producer", defaultValue = "5")
    private Integer lingerMs = 5;
    // linger.ms
    @UriParam(label = "producer", defaultValue = "60000")
    private Integer maxBlockMs = 60000;
    // max.request.size
    @UriParam(label = "producer", defaultValue = "1048576")
    private Integer maxRequestSize = 1048576;
    // request.timeout.ms
    @UriParam(label = "producer", defaultValue = "30000")
    private Integer requestTimeoutMs = 30000;
    // delivery.timeout.ms
    @UriParam(label = "producer", defaultValue = "120000")
    private Integer deliveryTimeoutMs = 120000;
    @UriParam(label = "producer,advanced")
    private boolean recordMetadata;
    // max.in.flight.requests.per.connection
    @UriParam(label = "producer", defaultValue = "5")
    private Integer maxInFlightRequest = 5;
    // enable.idempotence
    // reconnect.backoff.ms
    @UriParam(label = "producer", defaultValue = "true")
    private boolean enableIdempotence = true;
    @UriParam(label = "producer", description = "To use a custom KafkaHeaderSerializer to serialize kafka headers values")
    private KafkaHeaderSerializer headerSerializer = new DefaultKafkaHeaderSerializer();
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    @UriParam(label = "consumer")
    private boolean batching;
    @UriParam(label = "consumer")
    private Integer batchingIntervalMs;

    @UriParam(label = "producer")
    private String transactionalId;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean transacted;

    public KafkaConfiguration() {
    }

    /**
     * Returns a copy of this configuration
     */
    @Override
    public KafkaConfiguration copy() {
        return (KafkaConfiguration) super.copy();
    }

    public Properties createProducerProperties() {
        // Apply saslAuthType configuration if set (before creating properties)
        applyAuthTypeConfiguration();

        Properties props = new Properties();
        applyCommonClientProperties(props);
        addPropertyIfNotEmpty(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, getKeySerializer());
        addPropertyIfNotEmpty(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, getValueSerializer());
        addPropertyIfNotEmpty(props, ProducerConfig.ACKS_CONFIG, getRequestRequiredAcks());
        addPropertyIfNotEmpty(props, ProducerConfig.BUFFER_MEMORY_CONFIG, getBufferMemorySize());
        addPropertyIfNotEmpty(props, ProducerConfig.COMPRESSION_TYPE_CONFIG, getCompressionCodec());
        addPropertyIfNotEmpty(props, ProducerConfig.RETRIES_CONFIG, getRetries());
        addPropertyIfNotEmpty(props, ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, getInterceptorClasses());
        addPropertyIfNotEmpty(props, ProducerConfig.BATCH_SIZE_CONFIG, getProducerBatchSize());
        addPropertyIfNotEmpty(props, ProducerConfig.LINGER_MS_CONFIG, getLingerMs());
        addPropertyIfNotEmpty(props, ProducerConfig.MAX_BLOCK_MS_CONFIG, getMaxBlockMs());
        addPropertyIfNotEmpty(props, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, getMaxRequestSize());
        addPropertyIfNotEmpty(props, ProducerConfig.PARTITIONER_CLASS_CONFIG, getPartitioner());
        addPropertyIfNotEmpty(props, ProducerConfig.PARTITIONER_IGNORE_KEYS_CONFIG, isPartitionerIgnoreKeys());
        addPropertyIfNotEmpty(props, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, getRequestTimeoutMs());
        addPropertyIfNotEmpty(props, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, getDeliveryTimeoutMs());
        addPropertyIfNotEmpty(props, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, getMaxInFlightRequest());
        addPropertyIfNotEmpty(props, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, isEnableIdempotence());
        addPropertyIfNotEmpty(props, ProducerConfig.TRANSACTIONAL_ID_CONFIG, getTransactionalId());

        applySecurityProperties(props);
        applyAdditionalProperties(props);

        return props;
    }

    public Properties createConsumerProperties() {
        // Apply saslAuthType configuration if set (before creating properties)
        applyAuthTypeConfiguration();

        Properties props = new Properties();
        applyCommonClientProperties(props);
        addPropertyIfNotEmpty(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getKeyDeserializer());
        addPropertyIfNotEmpty(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getValueDeserializer());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MIN_BYTES_CONFIG, getFetchMinBytes());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MAX_BYTES_CONFIG, getFetchMaxBytes());
        // classic-only properties: skip when using the new consumer protocol (KIP-848)
        boolean classicProtocol = !"consumer".equals(getGroupProtocol());
        if (classicProtocol) {
            addPropertyIfNotEmpty(props, ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, getHeartbeatIntervalMs());
            addPropertyIfNotEmpty(props, ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, getSessionTimeoutMs());
        }
        addPropertyIfNotEmpty(props, ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, getMaxPartitionFetchBytes());
        addPropertyIfNotEmpty(props, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, getMaxPollIntervalMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, getMaxPollRecords());
        addPropertyIfNotEmpty(props, ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, getInterceptorClasses());
        addPropertyIfNotEmpty(props, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, getAutoOffsetReset());
        addPropertyIfNotEmpty(props, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, isAutoCommitEnable());
        if (classicProtocol) {
            addPropertyIfNotEmpty(props, ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, getPartitionAssignor());
        }
        addPropertyIfNotEmpty(props, ConsumerConfig.GROUP_PROTOCOL_CONFIG, getGroupProtocol());
        addPropertyIfNotEmpty(props, ConsumerConfig.GROUP_REMOTE_ASSIGNOR_CONFIG, getGroupRemoteAssignor());
        addPropertyIfNotEmpty(props, ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, getConsumerRequestTimeoutMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, getAutoCommitIntervalMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.CHECK_CRCS_CONFIG, getCheckCrcs());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, getFetchWaitMaxMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.ISOLATION_LEVEL_CONFIG, getIsolationLevel());
        addPropertyIfNotFalse(props, "specific.avro.reader", isSpecificAvroReader());

        applySecurityProperties(props);
        applyAdditionalProperties(props);

        return props;
    }

    public boolean isTopicIsPattern() {
        return topicIsPattern;
    }

    /**
     * Whether the topic is a pattern (regular expression). This can be used to subscribe to dynamic number of topics
     * matching the pattern.
     */
    public void setTopicIsPattern(boolean topicIsPattern) {
        this.topicIsPattern = topicIsPattern;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A string that uniquely identifies the group of consumer processes to which this consumer belongs. By setting the
     * same group id, multiple processes can indicate that they are all part of the same consumer group. This option is
     * required for consumers.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupInstanceId() {
        return groupInstanceId;
    }

    /**
     * A unique identifier of the consumer instance provided by the end user. Only non-empty strings are permitted. If
     * set, the consumer is treated as a static member, which means that only one instance with this ID is allowed in
     * the consumer group at any time. This can be used in combination with a larger session timeout to avoid group
     * rebalances caused by transient unavailability (e.g., process restarts). If not set, the consumer will join the
     * group as a dynamic member, which is the traditional behavior.
     */
    public void setGroupInstanceId(String groupInstanceId) {
        this.groupInstanceId = groupInstanceId;
    }

    public String getPartitioner() {
        return partitioner;
    }

    /**
     * The partitioner class for partitioning messages amongst sub-topics. The default partitioner is based on the hash
     * of the key.
     */
    public void setPartitioner(String partitioner) {
        this.partitioner = partitioner;
    }

    /**
     * Whether the message keys should be ignored when computing the partition. This setting has effect only when
     * {@link #partitioner} is not set
     */
    public boolean isPartitionerIgnoreKeys() {
        return partitionerIgnoreKeys;
    }

    public void setPartitionerIgnoreKeys(boolean partitionerIgnoreKeys) {
        this.partitionerIgnoreKeys = partitionerIgnoreKeys;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Name of the topic to use. On the consumer you can use comma to separate multiple topics. A producer can only send
     * a message to a single topic.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    /**
     * The number of consumers that connect to kafka server. Each consumer is run on a separate thread that retrieves
     * and process the incoming data.
     */
    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public boolean isAutoCommitEnable() {
        return offsetRepository == null && !batching && autoCommitEnable;
    }

    /**
     * @deprecated use {@link #isAutoCommitEnable()}
     */
    @Deprecated(since = "4.22.0")
    public boolean getAutoCommitEnable() {
        return isAutoCommitEnable();
    }

    /**
     * If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer. This committed
     * offset will be used when the process fails as the position from which the new consumer will begin.
     */
    public void setAutoCommitEnable(boolean autoCommitEnable) {
        this.autoCommitEnable = autoCommitEnable;
    }

    public boolean isAllowManualCommit() {
        return allowManualCommit;
    }

    /**
     * Whether to allow doing manual commits via {@link KafkaManualCommit}.
     * <p/>
     * If this option is enabled then an instance of {@link KafkaManualCommit} is stored on the {@link Exchange} message
     * header, which allows end users to access this API and perform manual offset commits via the Kafka consumer.
     */
    public void setAllowManualCommit(boolean allowManualCommit) {
        this.allowManualCommit = allowManualCommit;
    }

    public StateRepository<String, String> getOffsetRepository() {
        return offsetRepository;
    }

    /**
     * The offset repository to use to locally store the offset of each partition of the topic. Defining one will
     * disable the autocommit.
     */
    public void setOffsetRepository(StateRepository<String, String> offsetRepository) {
        this.offsetRepository = offsetRepository;
    }

    public Integer getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }

    /**
     * The frequency in ms that the consumer offsets are committed to zookeeper.
     */
    public void setAutoCommitIntervalMs(Integer autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public Integer getFetchMinBytes() {
        return fetchMinBytes;
    }

    /**
     * The minimum amount of data the server should return for a fetch request. If insufficient data is available, the
     * request will wait for that much data to accumulate before answering the request.
     */
    public void setFetchMinBytes(Integer fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    /**
     * The maximum amount of data the server should return for a fetch request. This is not an absolute maximum, if the
     * first message in the first non-empty partition of the fetch is larger than this value, the message will still be
     * returned to ensure that the consumer can make progress. The maximum message size accepted by the broker is
     * defined via message.max.bytes (broker config) or max.message.bytes (topic config). Note that the consumer
     * performs multiple fetches in parallel.
     */
    public Integer getFetchMaxBytes() {
        return fetchMaxBytes;
    }

    public void setFetchMaxBytes(Integer fetchMaxBytes) {
        this.fetchMaxBytes = fetchMaxBytes;
    }

    public Integer getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    /**
     * The maximum amount of time the server will block before answering the fetch request if there isn't enough data to
     * immediately satisfy fetch.min.bytes
     */
    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    /**
     * Where a consumer group starts reading when it has no committed offset, or the committed offset is out of range.
     * Valid values are: earliest (seek to the earliest available offset), latest (seek to the latest offset, the
     * default), none (throw an exception if no previous offset is found), by_duration: followed by an ISO-8601 duration
     * (e.g. by_duration:PT5M or by_duration:P1D; requires Kafka 4.0 or later).
     */
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public boolean isBreakOnFirstError() {
        return breakOnFirstError;
    }

    /**
     * This options controls what happens when a consumer is processing an exchange and it fails. If the option is
     * <tt>false</tt> then the consumer continues to the next message and processes it. If the option is <tt>true</tt>
     * then the consumer breaks out.
     *
     * Using the default NoopCommitManager will cause the consumer to not commit the offset so that the message is
     * re-attempted. The consumer should use the KafkaManualCommit to determine the best way to handle the message.
     *
     * Using either the SyncCommitManager or the AsyncCommitManager, the consumer will seek back to the offset of the
     * message that caused a failure, and then re-attempt to process this message. However, this can lead to endless
     * processing of the same message if it's bound to fail every time, e.g., a poison message. Therefore, it's
     * recommended to deal with that, for example, by using Camel's error handler.
     */
    public void setBreakOnFirstError(boolean breakOnFirstError) {
        this.breakOnFirstError = breakOnFirstError;
    }

    public String getCompressionCodec() {
        return compressionCodec;
    }

    /**
     * This parameter allows you to specify the compression codec for all data generated by this producer. Valid values
     * are "none", "gzip", "snappy", "lz4" and "zstd".
     */
    public void setCompressionCodec(String compressionCodec) {
        this.compressionCodec = compressionCodec;
    }

    public Integer getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * The amount of time the broker will wait trying to meet the request.required.acks requirement before sending back
     * an error to the client.
     */
    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public Integer getDeliveryTimeoutMs() {
        return deliveryTimeoutMs;
    }

    /**
     * An upper bound on the time to report success or failure after a call to send() returns. This limits the total
     * time that a record will be delayed prior to sending, the time to await acknowledgement from the broker (if
     * expected), and the time allowed for retriable send failures.
     */
    public void setDeliveryTimeoutMs(Integer deliveryTimeoutMs) {
        this.deliveryTimeoutMs = deliveryTimeoutMs;
    }

    /**
     * @deprecated This option has no effect since the old Scala Kafka producer was removed (CAMEL-9467). Use
     *             {@link #setBufferMemorySize(Integer)} or {@link #setMaxBlockMs(Integer)} instead.
     */
    @Deprecated
    public Integer getQueueBufferingMaxMessages() {
        return queueBufferingMaxMessages;
    }

    /**
     * @deprecated This option has no effect since the old Scala Kafka producer was removed (CAMEL-9467). Use
     *             {@link #setBufferMemorySize(Integer)} or {@link #setMaxBlockMs(Integer)} instead.
     */
    @Deprecated
    public void setQueueBufferingMaxMessages(Integer queueBufferingMaxMessages) {
        this.queueBufferingMaxMessages = queueBufferingMaxMessages;
    }

    public String getValueSerializer() {
        return valueSerializer;
    }

    /**
     * The serializer class for messages.
     */
    public void setValueSerializer(String valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    public String getKeySerializer() {
        return keySerializer;
    }

    /**
     * The serializer class for keys (defaults to the same as for messages if nothing is given).
     */
    public void setKeySerializer(String keySerializer) {
        this.keySerializer = keySerializer;
    }

    public Integer getBufferMemorySize() {
        return bufferMemorySize;
    }

    /**
     * The total bytes of memory the producer can use to buffer records waiting to be sent to the server. If records are
     * sent faster than they can be delivered to the server, the producer will either block or throw an exception based
     * on the preference specified by block.on.buffer.full.This setting should correspond roughly to the total memory
     * the producer will use, but is not a hard bound since not all memory the producer uses is used for buffering. Some
     * additional memory will be used for compression (if compression is enabled) as well as for maintaining in-flight
     * requests.
     */
    public void setBufferMemorySize(Integer bufferMemorySize) {
        this.bufferMemorySize = bufferMemorySize;
    }

    public String getKey() {
        return key;
    }

    /**
     * The record key (or null if no key is specified). If this option has been configured then it take precedence over
     * header {@link KafkaConstants#KEY}
     */
    public void setKey(String key) {
        this.key = key;
    }

    public Integer getPartitionKey() {
        return partitionKey;
    }

    /**
     * The partition to which the record will be sent (or null if no partition was specified). If this option has been
     * configured then it take precedence over header {@link KafkaConstants#PARTITION_KEY}
     */
    public void setPartitionKey(Integer partitionKey) {
        this.partitionKey = partitionKey;
    }

    public boolean isUseIterator() {
        return useIterator;
    }

    /**
     * Sets whether sending to kafka should send the message body as a single record, or use a java.util.Iterator to
     * send multiple records to kafka (if the message body can be iterated).
     */
    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    public String getRequestRequiredAcks() {
        return requestRequiredAcks;
    }

    /**
     * The number of acknowledgments the producer requires the leader to have received before considering a request
     * complete. This controls the durability of records that are sent. The following settings are allowed:
     *
     * acks=0 If set to zero, then the producer will not wait for any acknowledgment from the server at all. The record
     * will be immediately added to the socket buffer and considered sent. No guarantee can be made that the server has
     * received the record in this case, and the retry configuration will not take effect (as the client won't generally
     * know of any failures). The offset given back for each record will always be set to -1. acks=1 This will mean the
     * leader will write the record to its local log but will respond without awaiting full acknowledgment from all
     * followers. In this case should the leader fail immediately after acknowledging the record, but before the
     * followers have replicated it, then the record will be lost. acks=all This means the leader will wait for the full
     * set of in-sync replicas to acknowledge the record. This guarantees that the record will not be lost as long as at
     * least one in-sync replica remains alive. This is the strongest available guarantee. This is equivalent to the
     * acks=-1 setting. Note that enabling idempotence requires this config value to be 'all'. If conflicting
     * configurations are set and idempotence is not explicitly enabled, idempotence is disabled.
     */
    public void setRequestRequiredAcks(String requestRequiredAcks) {
        this.requestRequiredAcks = requestRequiredAcks;
    }

    public Integer getRetries() {
        return retries;
    }

    /**
     * Setting a value greater than zero will cause the client to resend any record that has failed to be sent due to a
     * potentially transient error. Note that this retry is no different from if the client re-sending the record upon
     * receiving the error. Produce requests will be failed before the number of retries has been exhausted if the
     * timeout configured by delivery.timeout.ms expires first before successful acknowledgement. Users should generally
     * prefer to leave this config unset and instead use delivery.timeout.ms to control retry behavior.
     *
     * Enabling idempotence requires this config value to be greater than 0. If conflicting configurations are set and
     * idempotence is not explicitly enabled, idempotence is disabled.
     *
     * Allowing retries while setting enable.idempotence to false and max.in.flight.requests.per.connection to 1 will
     * potentially change the ordering of records, because if two batches are sent to a single partition, and the first
     * fails and is retried but the second succeeds; then the records in the second batch may appear first.
     */
    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public Integer getProducerBatchSize() {
        return producerBatchSize;
    }

    /**
     * The producer will attempt to batch records together into fewer requests whenever multiple records are being sent
     * to the same partition. This helps performance on both the client and the server. This configuration controls the
     * default batch size in bytes. No attempt will be made to batch records larger than this size. Requests sent to
     * brokers will contain multiple batches, one for each partition with data available to be sent. A small batch size
     * will make batching less common and may reduce throughput (a batch size of zero will disable batching entirely). A
     * very large batch size may use memory a bit more wastefully as we will always allocate a buffer of the specified
     * batch size in anticipation of additional records.
     */
    public void setProducerBatchSize(Integer producerBatchSize) {
        this.producerBatchSize = producerBatchSize;
    }

    public boolean isBatchWithIndividualHeaders() {
        return batchWithIndividualHeaders;
    }

    /**
     * If this feature is enabled and a single element of a batch is an Exchange or Message, the producer will generate
     * individual kafka header values for it by using the batch Message to determine the values. Normal behavior
     * consists of always using the same header values (which are determined by the parent Exchange which contains the
     * Iterable or Iterator).
     */
    public void setBatchWithIndividualHeaders(boolean batchWithIndividualHeaders) {
        this.batchWithIndividualHeaders = batchWithIndividualHeaders;
    }

    public Integer getLingerMs() {
        return lingerMs;
    }

    /**
     * The producer groups together any records that arrive in between request transmissions into a single, batched,
     * request. Normally, this occurs only under load when records arrive faster than they can be sent out. However, in
     * some circumstances, the client may want to reduce the number of requests even under a moderate load. This setting
     * achieves this by adding a small amount of artificial delay. That is, rather than immediately sending out a
     * record, the producer will wait for up to the given delay to allow other records to be sent so that they can be
     * batched together. This can be thought of as analogous to Nagle's algorithm in TCP. This setting gives the upper
     * bound on the delay for batching: once we get batch.size worth of records for a partition, it will be sent
     * immediately regardless of this setting, however, if we have fewer than this many bytes accumulated for this
     * partition, we will 'linger' for the specified time waiting for more records to show up. This setting defaults to
     * 0 (i.e., no delay). Setting linger.ms=5, for example, would have the effect of reducing the number of requests
     * sent but would add up to 5ms of latency to records sent in the absence of load.
     */
    public void setLingerMs(Integer lingerMs) {
        this.lingerMs = lingerMs;
    }

    public Integer getMaxBlockMs() {
        return maxBlockMs;
    }

    /**
     * The configuration controls how long the KafkaProducer's send(), partitionsFor(), initTransactions(),
     * sendOffsetsToTransaction(), commitTransaction() and abortTransaction() methods will block. For send() this
     * timeout bounds the total time waiting for both metadata fetch and buffer allocation (blocking in the
     * user-supplied serializers or partitioner is not counted against this timeout). For partitionsFor() this time out
     * bounds the time spent waiting for metadata if it is unavailable. The transaction-related methods always block,
     * but may time out if the transaction coordinator could not be discovered or did not respond within the timeout.
     */
    public void setMaxBlockMs(Integer maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public Integer getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * The maximum size of a request. This is also effectively a cap on the maximum record size. Note that the server
     * has its own cap on record size which may be different from this. This setting will limit the number of record
     * batches the producer will send in a single request to avoid sending huge requests.
     */
    public void setMaxRequestSize(Integer maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public Integer getMaxInFlightRequest() {
        return maxInFlightRequest;
    }

    /**
     * The maximum number of unacknowledged requests the client will send on a single connection before blocking. Note
     * that if this setting is set to be greater than 1 and there are failed sends, there is a risk of message
     * re-ordering due to retries (i.e., if retries are enabled).
     */
    public void setMaxInFlightRequest(Integer maxInFlightRequest) {
        this.maxInFlightRequest = maxInFlightRequest;
    }

    public Integer getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * The expected time between heartbeats to the consumer coordinator when using Kafka's group management facilities.
     * Heartbeats are used to ensure that the consumer's session stays active and to facilitate rebalancing when new
     * consumers join or leave the group. The value must be set lower than session.timeout.ms, but typically should be
     * set no higher than 1/3 of that value. It can be adjusted even lower to control the expected time for normal
     * rebalances.
     */
    public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public Integer getMaxPartitionFetchBytes() {
        return maxPartitionFetchBytes;
    }

    /**
     * The maximum amount of data per-partition the server will return. The maximum total memory used for a request will
     * be #partitions * max.partition.fetch.bytes. This size must be at least as large as the maximum message size the
     * server allows or else it is possible for the producer to send messages larger than the consumer can fetch. If
     * that happens, the consumer can get stuck trying to fetch a large message on a certain partition.
     */
    public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
        this.maxPartitionFetchBytes = maxPartitionFetchBytes;
    }

    public Integer getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * The timeout used to detect failures when using Kafka's group management facilities.
     */
    public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public Integer getMaxPollRecords() {
        return maxPollRecords;
    }

    /**
     * The maximum number of records returned in a single call to poll()
     */
    public void setMaxPollRecords(Integer maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public Long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * The timeout used when polling the KafkaConsumer.
     */
    public void setPollTimeoutMs(Long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public Integer getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    /**
     * The maximum delay between invocations of poll() when using consumer group management. This places an upper bound
     * on the amount of time that the consumer can be idle before fetching more records. If poll() is not called before
     * expiration of this timeout, then the consumer is considered failed, and the group will re-balance to reassign the
     * partitions to another member.
     */
    public void setMaxPollIntervalMs(Integer maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    public String getPartitionAssignor() {
        return partitionAssignor;
    }

    /**
     * The class name of the partition assignment strategy that the client will use to distribute partition ownership
     * amongst consumer instances when group management is used
     */
    public void setPartitionAssignor(String partitionAssignor) {
        this.partitionAssignor = partitionAssignor;
    }

    public String getGroupProtocol() {
        return groupProtocol;
    }

    /**
     * The consumer group protocol to use. The "classic" protocol uses the traditional partition assignment and
     * rebalancing mechanism. The "consumer" protocol enables the new KIP-848 consumer rebalance protocol which provides
     * faster and more efficient rebalancing.
     *
     * When set to "consumer", classic-only properties (heartbeatIntervalMs, sessionTimeoutMs, partitionAssignor) are
     * automatically excluded from the consumer configuration.
     */
    public void setGroupProtocol(String groupProtocol) {
        this.groupProtocol = groupProtocol;
    }

    public String getGroupRemoteAssignor() {
        return groupRemoteAssignor;
    }

    /**
     * The name of the server-side assignor to use when group.protocol is set to "consumer". If not specified, the group
     * coordinator will use the default assignor configured on the broker (group.consumer.assignors).
     */
    public void setGroupRemoteAssignor(String groupRemoteAssignor) {
        this.groupRemoteAssignor = groupRemoteAssignor;
    }

    public Integer getConsumerRequestTimeoutMs() {
        return consumerRequestTimeoutMs;
    }

    /**
     * The configuration controls the maximum amount of time the client will wait for the response of a request. If the
     * response is not received before the timeout elapsed, the client will resend the request if necessary or fail the
     * request if retries are exhausted.
     */
    public void setConsumerRequestTimeoutMs(Integer consumerRequestTimeoutMs) {
        this.consumerRequestTimeoutMs = consumerRequestTimeoutMs;
    }

    public Boolean getCheckCrcs() {
        return checkCrcs;
    }

    /**
     * Automatically check the CRC32 of the records consumed. This ensures no on-the-wire or on-disk corruption to the
     * messages occurred. This check adds some overhead, so it may be disabled in cases seeking extreme performance.
     */
    public void setCheckCrcs(Boolean checkCrcs) {
        this.checkCrcs = checkCrcs;
    }

    public SeekPolicy getSeekTo() {
        return seekTo;
    }

    /**
     * Set if KafkaConsumer should read from the beginning or the end on startup: SeekPolicy.BEGINNING: read from the
     * beginning. SeekPolicy.END: read from the end.
     */
    public void setSeekTo(SeekPolicy seekTo) {
        this.seekTo = seekTo;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    /**
     * To use a custom worker pool for continue routing {@link Exchange} after kafka server has acknowledged the message
     * that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing. If using this option,
     * then you must handle the lifecycle of the thread pool to shut the pool down when no longer needed.
     */
    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

    public Integer getWorkerPoolCoreSize() {
        return workerPoolCoreSize;
    }

    /**
     * Number of core threads for the worker pool for continue routing {@link Exchange} after kafka server has
     * acknowledged the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking
     * processing.
     */
    public void setWorkerPoolCoreSize(Integer workerPoolCoreSize) {
        this.workerPoolCoreSize = workerPoolCoreSize;
    }

    public Integer getWorkerPoolMaxSize() {
        return workerPoolMaxSize;
    }

    /**
     * Maximum number of threads for the worker pool for continue routing {@link Exchange} after kafka server has
     * acknowledged the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking
     * processing.
     */
    public void setWorkerPoolMaxSize(Integer workerPoolMaxSize) {
        this.workerPoolMaxSize = workerPoolMaxSize;
    }

    public boolean isRecordMetadata() {
        return recordMetadata;
    }

    /**
     * Whether the producer should store the {@link RecordMetadata} results from sending to Kafka. The results are
     * stored in a {@link List} containing the {@link RecordMetadata} metadata's. The list is stored on a header with
     * the key {@link KafkaConstants#KAFKA_RECORD_META}
     */
    public void setRecordMetadata(boolean recordMetadata) {
        this.recordMetadata = recordMetadata;
    }

    public String getInterceptorClasses() {
        return interceptorClasses;
    }

    /**
     * Sets interceptors for producer or consumers. Producer interceptors have to be classes implementing
     * {@link org.apache.kafka.clients.producer.ProducerInterceptor} Consumer interceptors have to be classes
     * implementing {@link org.apache.kafka.clients.consumer.ConsumerInterceptor} Note that if you use Producer
     * interceptor on a consumer it will throw a class cast exception in runtime
     */
    public void setInterceptorClasses(String interceptorClasses) {
        this.interceptorClasses = interceptorClasses;
    }

    public boolean isEnableIdempotence() {
        return enableIdempotence;
    }

    /**
     * When set to 'true', the producer will ensure that exactly one copy of each message is written in the stream. If
     * 'false', producer retries due to broker failures, etc., may write duplicates of the retried message in the
     * stream. Note that enabling idempotence requires max.in.flight.requests.per.connection to be less than or equal to
     * 5 (with message ordering preserved for any allowable value), retries to be greater than 0, and acks must be
     * 'all'.
     *
     * Idempotence is enabled by default if no conflicting configurations are set. If conflicting configurations are set
     * and idempotence is not explicitly enabled, idempotence is disabled. If idempotence is explicitly enabled and
     * conflicting configurations are set, a ConfigException is thrown.
     */
    public void setEnableIdempotence(boolean enableIdempotence) {
        this.enableIdempotence = enableIdempotence;
    }

    public KafkaHeaderSerializer getHeaderSerializer() {
        return headerSerializer;
    }

    /**
     * Sets custom KafkaHeaderDeserializer for serialization camel headers values to kafka headers values.
     *
     * @param headerSerializer custom kafka header serializer to be used
     */
    public void setHeaderSerializer(final KafkaHeaderSerializer headerSerializer) {
        this.headerSerializer = headerSerializer;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public PollOnError getPollOnError() {
        return pollOnError;
    }

    /**
     * What to do if kafka threw an exception while polling for new messages.
     *
     * Will by default use the value from the component configuration unless an explicit value has been configured on
     * the endpoint level.
     *
     * DISCARD will discard the message and continue to poll the next message. ERROR_HANDLER will use Camel's error
     * handler to process the exception, and afterwards continue to poll the next message. RECONNECT will re-connect the
     * consumer and try polling the message again. RETRY will let the consumer retry poll the same message again. STOP
     * will stop the consumer (it has to be manually started/restarted if the consumer should be able to consume
     * messages again)
     */
    public void setPollOnError(PollOnError pollOnError) {
        this.pollOnError = pollOnError;
    }

    public Long getCommitTimeoutMs() {
        return commitTimeoutMs;
    }

    /**
     * The maximum time, in milliseconds, that the code will wait for a synchronous commit to complete
     */
    public void setCommitTimeoutMs(Long commitTimeoutMs) {
        this.commitTimeoutMs = commitTimeoutMs;
    }

    public String getIsolationLevel() {
        return isolationLevel;
    }

    /**
     * Controls how to read messages written transactionally. If set to read_committed, consumer.poll() will only return
     * transactional messages which have been committed. If set to read_uncommitted (the default), consumer.poll() will
     * return all messages, even transactional messages which have been aborted. Non-transactional messages will be
     * returned unconditionally in either mode. Messages will always be returned in offset order. Hence, in
     * read_committed mode, consumer.poll() will only return messages up to the last stable offset (LSO), which is the
     * one less than the offset of the first open transaction. In particular, any messages appearing after messages
     * belonging to ongoing transactions will be withheld until the relevant transaction has been completed. As a
     * result, read_committed</code> consumers will not be able to read up to the high watermark when there are in
     * flight transactions. Further, when in read_committed the seekToEnd method will return the LSO
     */
    public void setIsolationLevel(String isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public boolean isBatching() {
        return batching;
    }

    /**
     * Whether to use batching for processing or streaming. The default is false, which uses streaming.
     *
     * In streaming mode, then a single kafka record is processed per Camel exchange in the message body.
     *
     * In batching mode, then Camel groups many kafka records together as a List<Exchange> objects in the message body.
     * The option maxPollRecords is used to define the number of records to group together in batching mode.
     */
    public void setBatching(boolean batching) {
        this.batching = batching;
    }

    public Integer getBatchingIntervalMs() {
        return batchingIntervalMs;
    }

    /**
     * In consumer batching mode, then this option is specifying a time in millis, to trigger batch completion eager
     * when the current batch size has not reached the maximum size defined by maxPollRecords.
     *
     * Notice the trigger is not exact at the given interval, as this can only happen between kafka polls (see
     * pollTimeoutMs option). So for example setting this to 10000, then the trigger happens in the interval 10000 +
     * pollTimeoutMs. The default value for pollTimeoutMs is 5000, so this would mean a trigger interval at about every
     * 15 seconds.
     */
    public void setBatchingIntervalMs(Integer batchingIntervalMs) {
        this.batchingIntervalMs = batchingIntervalMs;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Indicates to create a transactional.id kafka property by using the endpoint id and route id. This property is
     * ignored in case there is transactional.id kafka property or the transactionalId parameter.
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    public String getTransactionalId() {
        return transactionalId;
    }

    /**
     * Enable the kafka producer to be a transactional one by setting the transactional.id property. In case this
     * property is used, the transacted parameter is ignored.
     */
    public void setTransactionalId(String transactionalId) {
        this.transactionalId = transactionalId;
    }

}
