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
package org.apache.camel.component.kafka.share;

import java.util.List;
import java.util.Properties;

import org.apache.camel.component.kafka.KafkaClientConfiguration;
import org.apache.camel.component.kafka.PollOnError;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;

@UriParams
public class KafkaShareConfiguration extends KafkaClientConfiguration {

    /**
     * The value of share.acknowledgement.mode: every record is acknowledged by the consumer.
     */
    static final String EXPLICIT_ACKNOWLEDGEMENT = "explicit";

    /**
     * The consumer group options that a share consumer rejects (see ShareConsumerConfig in kafka-clients).
     */
    static final List<String> SHARE_GROUP_UNSUPPORTED_CONFIGS = List.of(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            ConsumerConfig.GROUP_INSTANCE_ID_CONFIG,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG,
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
            ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
            ConsumerConfig.GROUP_PROTOCOL_CONFIG,
            ConsumerConfig.GROUP_REMOTE_ASSIGNOR_CONFIG);

    @UriPath(label = "common")
    @Metadata(required = true)
    private String topic;
    @UriParam(label = "consumer")
    @Metadata(required = true)
    private String groupId;
    @UriParam(label = "consumer", defaultValue = "1")
    private int consumersCount = 1;
    @UriParam(label = "consumer", defaultValue = "5000", javaType = "java.time.Duration")
    private Long pollTimeoutMs = 5000L;
    // max.poll.records
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer maxPollRecords = 500;
    // share.acquire.mode
    @UriParam(label = "consumer,advanced", defaultValue = "batch_optimized", enums = "batch_optimized,record_limit")
    private String acquireMode = "batch_optimized";
    // fetch.min.bytes
    @UriParam(label = "consumer", defaultValue = "1")
    private Integer fetchMinBytes = 1;
    // fetch.max.bytes
    @UriParam(label = "consumer", defaultValue = "52428800")
    private Integer fetchMaxBytes = 50 * 1024 * 1024;
    // fetch.max.wait.ms
    @UriParam(label = "consumer", defaultValue = "500")
    private Integer fetchWaitMaxMs = 500;
    // max.partition.fetch.bytes
    @UriParam(label = "consumer", defaultValue = "1048576")
    private Integer maxPartitionFetchBytes = 1048576;
    // request.timeout.ms
    @UriParam(label = "consumer", defaultValue = "30000")
    private Integer consumerRequestTimeoutMs = 30000;
    // check.crcs
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean checkCrcs = true;
    @UriParam(label = "consumer", defaultValue = "RELEASE", enums = "ACCEPT,RELEASE,REJECT")
    private KafkaShareAcknowledgeType onFailure = KafkaShareAcknowledgeType.RELEASE;
    @UriParam(label = "consumer", defaultValue = "SYNC", enums = "SYNC,ASYNC")
    private KafkaShareCommitMode commitMode = KafkaShareCommitMode.SYNC;
    @UriParam(label = "consumer", defaultValue = "5000", javaType = "java.time.Duration")
    private Long commitTimeoutMs = 5000L;
    @UriParam(label = "consumer", defaultValue = "ERROR_HANDLER", enums = "DISCARD,ERROR_HANDLER,RECONNECT,RETRY,STOP")
    private PollOnError pollOnError = PollOnError.ERROR_HANDLER;

    /**
     * Returns a copy of this configuration
     */
    @Override
    public KafkaShareConfiguration copy() {
        return (KafkaShareConfiguration) super.copy();
    }

    /**
     * Creates the properties of the share consumer. The properties never contain the consumer group options that a
     * share consumer rejects (such as auto.offset.reset, enable.auto.commit or group.protocol), unless they are set as
     * additional properties.
     */
    public Properties createShareConsumerProperties() {
        // Apply saslAuthType configuration if set (before creating properties)
        applyAuthTypeConfiguration();

        Properties props = new Properties();
        applyCommonClientProperties(props);
        addPropertyIfNotEmpty(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getKeyDeserializer());
        addPropertyIfNotEmpty(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getValueDeserializer());
        addPropertyIfNotEmpty(props, ConsumerConfig.GROUP_ID_CONFIG, getGroupId());
        props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, EXPLICIT_ACKNOWLEDGEMENT);
        addPropertyIfNotEmpty(props, ConsumerConfig.SHARE_ACQUIRE_MODE_CONFIG, getAcquireMode());
        addPropertyIfNotEmpty(props, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, getMaxPollRecords());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MIN_BYTES_CONFIG, getFetchMinBytes());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MAX_BYTES_CONFIG, getFetchMaxBytes());
        addPropertyIfNotEmpty(props, ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, getFetchWaitMaxMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, getMaxPartitionFetchBytes());
        addPropertyIfNotEmpty(props, ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, getConsumerRequestTimeoutMs());
        addPropertyIfNotEmpty(props, ConsumerConfig.CHECK_CRCS_CONFIG, getCheckCrcs());
        addPropertyIfNotFalse(props, "specific.avro.reader", isSpecificAvroReader());

        applySecurityProperties(props);
        applyAdditionalProperties(props);

        return props;
    }

    /**
     * Fails when the properties of a share consumer contain consumer group options, which can only be set as additional
     * properties. The share consumer would reject them every time it is created.
     */
    static void validateShareConsumerProperties(Properties props) {
        List<String> unsupported = SHARE_GROUP_UNSUPPORTED_CONFIGS.stream().filter(props::containsKey).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "The consumer group options " + unsupported + " cannot be set on a share consumer;"
                                               + " remove them from the additional properties");
        }
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Name of the topic to consume from. Use comma to separate multiple topics. Topic patterns are not supported by
     * share groups.
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * The name of the share group. All the consumers that use the same share group name share the records of the
     * topics: each record is delivered to one of them.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    /**
     * The number of consumers that connect to the Kafka server. Each consumer runs on its own thread and receives
     * records, as the records of a partition are shared by all the consumers of a share group. Unlike a consumer group,
     * the number of consumers is not limited by the number of partitions.
     */
    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public Long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * The timeout used when polling the share consumer.
     */
    public void setPollTimeoutMs(Long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public Integer getMaxPollRecords() {
        return maxPollRecords;
    }

    /**
     * The maximum number of records returned in a single call to poll(). With the batch_optimized acquire mode, a poll
     * can return more records, to align with the batches of the topic.
     */
    public void setMaxPollRecords(Integer maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public String getAcquireMode() {
        return acquireMode;
    }

    /**
     * How the share consumer acquires records. With record_limit, a poll() returns at most maxPollRecords records. With
     * batch_optimized, a poll() can return more records than maxPollRecords, to align with the batches of the topic.
     */
    public void setAcquireMode(String acquireMode) {
        this.acquireMode = acquireMode;
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

    public Integer getFetchMaxBytes() {
        return fetchMaxBytes;
    }

    /**
     * The maximum amount of data the server should return for a fetch request. This is not an absolute maximum: if the
     * first record batch in the first non-empty partition of the fetch is larger than this value, the record batch will
     * still be returned to ensure that the consumer can make progress.
     */
    public void setFetchMaxBytes(Integer fetchMaxBytes) {
        this.fetchMaxBytes = fetchMaxBytes;
    }

    public Integer getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    /**
     * The maximum amount of time the server will block before answering the fetch request if there isn't enough data to
     * immediately satisfy fetch.min.bytes.
     */
    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public Integer getMaxPartitionFetchBytes() {
        return maxPartitionFetchBytes;
    }

    /**
     * The maximum amount of data per-partition the server will return.
     */
    public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
        this.maxPartitionFetchBytes = maxPartitionFetchBytes;
    }

    public Integer getConsumerRequestTimeoutMs() {
        return consumerRequestTimeoutMs;
    }

    /**
     * The configuration controls the maximum amount of time the client will wait for the response of a request. If the
     * response is not received before the timeout elapses, the client will resend the request if necessary or fail the
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
     * messages occurred.
     */
    public void setCheckCrcs(Boolean checkCrcs) {
        this.checkCrcs = checkCrcs;
    }

    public KafkaShareAcknowledgeType getOnFailure() {
        return onFailure;
    }

    /**
     * How to acknowledge a record whose exchange failed or was rolled back. RELEASE makes the record available again,
     * to this or another consumer, until the broker delivery count limit is reached. REJECT discards the record. ACCEPT
     * marks the record as consumed.
     */
    public void setOnFailure(KafkaShareAcknowledgeType onFailure) {
        this.onFailure = onFailure;
    }

    public KafkaShareCommitMode getCommitMode() {
        return commitMode;
    }

    /**
     * How the acknowledgements of a poll are committed to the broker. SYNC commits them after the records of the poll
     * are processed, and waits for the result, so a failure to commit is reported to the exception handler. ASYNC
     * commits them without waiting, and a failure to commit is logged.
     */
    public void setCommitMode(KafkaShareCommitMode commitMode) {
        this.commitMode = commitMode;
    }

    public Long getCommitTimeoutMs() {
        return commitTimeoutMs;
    }

    /**
     * The maximum time to wait for the acknowledgements to be committed, when commitMode is SYNC.
     */
    public void setCommitTimeoutMs(Long commitTimeoutMs) {
        this.commitTimeoutMs = commitTimeoutMs;
    }

    public PollOnError getPollOnError() {
        return pollOnError;
    }

    /**
     * What to do if the share consumer throws an exception while polling for new records. DISCARD and RETRY log the
     * exception and poll again: unlike the kafka component, there is no record to skip or to retry, as the poll itself
     * failed, and the records that fail in the route are handled with onFailure. ERROR_HANDLER lets the exception
     * handler of the consumer handle the exception, and polls again. RECONNECT closes the share consumer and creates a
     * new one. STOP stops consuming. An authentication or authorization failure always stops consuming.
     */
    public void setPollOnError(PollOnError pollOnError) {
        this.pollOnError = pollOnError;
    }
}
