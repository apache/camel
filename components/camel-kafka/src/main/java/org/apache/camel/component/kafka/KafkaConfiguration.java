/**
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

import java.util.Properties;

import kafka.producer.DefaultPartitioner;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class KafkaConfiguration {

    @UriParam
    private String zookeeperConnect;
    @UriParam
    private String zookeeperHost;
    @UriParam(defaultValue = "2181")
    private int zookeeperPort = 2181;
    @UriParam
    private String topic;
    @UriParam
    private String groupId;
    @UriParam(defaultValue = "DefaultPartitioner")
    private String partitioner = DefaultPartitioner.class.getCanonicalName();
    @UriParam(defaultValue = "10")
    private int consumerStreams = 10;
    @UriParam(defaultValue = "1")
    private int consumersCount = 1;
    @UriParam(defaultValue = "100")
    private int batchSize = 100;
    @UriParam(defaultValue = "10000")
    private int barrierAwaitTimeoutMs = 10000;

    //Common configuration properties
    @UriParam
    private String clientId;

    //Consumer configuration properties
    @UriParam
    private String consumerId;
    @UriParam
    private Integer socketTimeoutMs;
    @UriParam
    private Integer socketReceiveBufferBytes;
    @UriParam
    private Integer fetchMessageMaxBytes;
    @UriParam
    private Boolean autoCommitEnable;
    @UriParam
    private Integer autoCommitIntervalMs;
    @UriParam
    private Integer queuedMaxMessages;
    @UriParam
    private Integer rebalanceMaxRetries;
    @UriParam
    private Integer fetchMinBytes;
    @UriParam
    private Integer fetchWaitMaxMs;
    @UriParam
    private Integer rebalanceBackoffMs;
    @UriParam
    private Integer refreshLeaderBackoffMs;
    @UriParam
    private String autoOffsetReset;
    @UriParam
    private Integer consumerTimeoutMs;

    //Zookeepr configuration properties
    @UriParam
    private Integer zookeeperSessionTimeoutMs;
    @UriParam
    private Integer zookeeperConnectionTimeoutMs;
    @UriParam
    private Integer zookeeperSyncTimeMs;

    //Producer configuration properties
    @UriParam
    private String producerType;
    @UriParam
    private String compressionCodec;
    @UriParam
    private String compressedTopics;
    @UriParam
    private Integer messageSendMaxRetries;
    @UriParam
    private Integer retryBackoffMs;
    @UriParam
    private Integer topicMetadataRefreshIntervalMs;

    //Sync producer config
    @UriParam
    private Integer sendBufferBytes;
    @UriParam
    private short requestRequiredAcks;
    @UriParam
    private Integer requestTimeoutMs;

    //Async producer config
    @UriParam
    private Integer queueBufferingMaxMs;
    @UriParam
    private Integer queueBufferingMaxMessages;
    @UriParam
    private Integer queueEnqueueTimeoutMs;
    @UriParam
    private Integer batchNumMessages;
    @UriParam
    private String serializerClass;
    @UriParam
    private String keySerializerClass;

    public KafkaConfiguration() {
    }

    public Properties createProducerProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, "request.required.acks", getRequestRequiredAcks());
        addPropertyIfNotNull(props, "partitioner.class", getPartitioner());
        addPropertyIfNotNull(props, "serializer.class", getSerializerClass());
        addPropertyIfNotNull(props, "key.serializer.class", getKeySerializerClass());
        addPropertyIfNotNull(props, "request.timeout.ms", getRequestTimeoutMs());
        addPropertyIfNotNull(props, "producer.type", getProducerType());
        addPropertyIfNotNull(props, "compression.codec", getCompressionCodec());
        addPropertyIfNotNull(props, "compressed.topics", getCompressedTopics());
        addPropertyIfNotNull(props, "message.send.max.retries", getMessageSendMaxRetries());
        addPropertyIfNotNull(props, "retry.backoff.ms", getRetryBackoffMs());
        addPropertyIfNotNull(props, "topic.metadata.refresh.interval.ms", getTopicMetadataRefreshIntervalMs());
        addPropertyIfNotNull(props, "queue.buffering.max.ms", getQueueBufferingMaxMs());
        addPropertyIfNotNull(props, "queue.buffering.max.messages", getQueueBufferingMaxMessages());
        addPropertyIfNotNull(props, "queue.enqueue.timeout.ms", getQueueEnqueueTimeoutMs());
        addPropertyIfNotNull(props, "batch.num.messages", getBatchNumMessages());
        addPropertyIfNotNull(props, "send.buffer.bytes", getSendBufferBytes());
        addPropertyIfNotNull(props, "client.id", getClientId());
        return props;
    }

    public Properties createConsumerProperties() {
        Properties props = new Properties();
        addPropertyIfNotNull(props, "consumer.id", getConsumerId());
        addPropertyIfNotNull(props, "socket.timeout.ms", getSocketTimeoutMs());
        addPropertyIfNotNull(props, "socket.receive.buffer.bytes", getSocketReceiveBufferBytes());
        addPropertyIfNotNull(props, "fetch.message.max.bytes", getFetchMessageMaxBytes());
        addPropertyIfNotNull(props, "auto.commit.enable", isAutoCommitEnable());
        addPropertyIfNotNull(props, "auto.commit.interval.ms", getAutoCommitIntervalMs());
        addPropertyIfNotNull(props, "queued.max.message.chunks", getQueueBufferingMaxMessages());
        addPropertyIfNotNull(props, "fetch.min.bytes", getFetchMinBytes());
        addPropertyIfNotNull(props, "fetch.wait.max.ms", getFetchWaitMaxMs());
        addPropertyIfNotNull(props, "rebalance.max.retries", getRebalanceMaxRetries());
        addPropertyIfNotNull(props, "rebalance.backoff.ms", getRebalanceBackoffMs());
        addPropertyIfNotNull(props, "refresh.leader.backoff.ms", getRefreshLeaderBackoffMs());
        addPropertyIfNotNull(props, "auto.offset.reset", getAutoOffsetReset());
        addPropertyIfNotNull(props, "consumer.timeout.ms", getConsumerTimeoutMs());
        addPropertyIfNotNull(props, "client.id", getClientId());
        addPropertyIfNotNull(props, "zookeeper.session.timeout.ms", getZookeeperSessionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.connection.timeout.ms", getZookeeperConnectionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.sync.time.ms", getZookeeperSyncTimeMs());
        return props;
    }

    private static <T> void addPropertyIfNotNull(Properties props, String key, T value) {
        if (value != null) {
            // Kafka expects all properties as String
            props.put(key, value.toString());
        }
    }

    public String getZookeeperConnect() {
        if (this.zookeeperConnect != null) {
            return zookeeperConnect;
        } else {
            return getZookeeperHost() + ":" + getZookeeperPort();
        }
    }

    public void setZookeeperConnect(String zookeeperConnect) {
        this.zookeeperConnect = zookeeperConnect;

        // connect overrides host and port
        this.zookeeperHost = null;
        this.zookeeperPort = -1;
    }

    public String getZookeeperHost() {
        return zookeeperHost;
    }

    public void setZookeeperHost(String zookeeperHost) {
        if (this.zookeeperConnect == null) {
            this.zookeeperHost = zookeeperHost;
        }
    }

    public int getZookeeperPort() {
        return zookeeperPort;
    }

    public void setZookeeperPort(int zookeeperPort) {
        if (this.zookeeperConnect == null) {
            this.zookeeperPort = zookeeperPort;
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getPartitioner() {
        return partitioner;
    }

    public void setPartitioner(String partitioner) {
        this.partitioner = partitioner;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getConsumerStreams() {
        return consumerStreams;
    }

    public void setConsumerStreams(int consumerStreams) {
        this.consumerStreams = consumerStreams;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBarrierAwaitTimeoutMs() {
        return barrierAwaitTimeoutMs;
    }

    public void setBarrierAwaitTimeoutMs(int barrierAwaitTimeoutMs) {
        this.barrierAwaitTimeoutMs = barrierAwaitTimeoutMs;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public Integer getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(Integer socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public Integer getSocketReceiveBufferBytes() {
        return socketReceiveBufferBytes;
    }

    public void setSocketReceiveBufferBytes(Integer socketReceiveBufferBytes) {
        this.socketReceiveBufferBytes = socketReceiveBufferBytes;
    }

    public Integer getFetchMessageMaxBytes() {
        return fetchMessageMaxBytes;
    }

    public void setFetchMessageMaxBytes(Integer fetchMessageMaxBytes) {
        this.fetchMessageMaxBytes = fetchMessageMaxBytes;
    }

    public Boolean isAutoCommitEnable() {
        return autoCommitEnable;
    }

    public void setAutoCommitEnable(Boolean autoCommitEnable) {
        this.autoCommitEnable = autoCommitEnable;
    }

    public Integer getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }

    public void setAutoCommitIntervalMs(Integer autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public Integer getQueuedMaxMessages() {
        return queuedMaxMessages;
    }

    public void setQueuedMaxMessages(Integer queuedMaxMessages) {
        this.queuedMaxMessages = queuedMaxMessages;
    }

    public Integer getRebalanceMaxRetries() {
        return rebalanceMaxRetries;
    }

    public void setRebalanceMaxRetries(Integer rebalanceMaxRetries) {
        this.rebalanceMaxRetries = rebalanceMaxRetries;
    }

    public Integer getFetchMinBytes() {
        return fetchMinBytes;
    }

    public void setFetchMinBytes(Integer fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public Integer getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public Integer getRebalanceBackoffMs() {
        return rebalanceBackoffMs;
    }

    public void setRebalanceBackoffMs(Integer rebalanceBackoffMs) {
        this.rebalanceBackoffMs = rebalanceBackoffMs;
    }

    public Integer getRefreshLeaderBackoffMs() {
        return refreshLeaderBackoffMs;
    }

    public void setRefreshLeaderBackoffMs(Integer refreshLeaderBackoffMs) {
        this.refreshLeaderBackoffMs = refreshLeaderBackoffMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public Integer getConsumerTimeoutMs() {
        return consumerTimeoutMs;
    }

    public void setConsumerTimeoutMs(Integer consumerTimeoutMs) {
        this.consumerTimeoutMs = consumerTimeoutMs;
    }

    public Integer getZookeeperSessionTimeoutMs() {
        return zookeeperSessionTimeoutMs;
    }

    public void setZookeeperSessionTimeoutMs(Integer zookeeperSessionTimeoutMs) {
        this.zookeeperSessionTimeoutMs = zookeeperSessionTimeoutMs;
    }

    public Integer getZookeeperConnectionTimeoutMs() {
        return zookeeperConnectionTimeoutMs;
    }

    public void setZookeeperConnectionTimeoutMs(Integer zookeeperConnectionTimeoutMs) {
        this.zookeeperConnectionTimeoutMs = zookeeperConnectionTimeoutMs;
    }

    public Integer getZookeeperSyncTimeMs() {
        return zookeeperSyncTimeMs;
    }

    public void setZookeeperSyncTimeMs(Integer zookeeperSyncTimeMs) {
        this.zookeeperSyncTimeMs = zookeeperSyncTimeMs;
    }

    public String getProducerType() {
        return producerType;
    }

    public void setProducerType(String producerType) {
        this.producerType = producerType;
    }

    public String getCompressionCodec() {
        return compressionCodec;
    }

    public void setCompressionCodec(String compressionCodec) {
        this.compressionCodec = compressionCodec;
    }

    public String getCompressedTopics() {
        return compressedTopics;
    }

    public void setCompressedTopics(String compressedTopics) {
        this.compressedTopics = compressedTopics;
    }

    public Integer getMessageSendMaxRetries() {
        return messageSendMaxRetries;
    }

    public void setMessageSendMaxRetries(Integer messageSendMaxRetries) {
        this.messageSendMaxRetries = messageSendMaxRetries;
    }

    public Integer getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(Integer retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public Integer getTopicMetadataRefreshIntervalMs() {
        return topicMetadataRefreshIntervalMs;
    }

    public void setTopicMetadataRefreshIntervalMs(Integer topicMetadataRefreshIntervalMs) {
        this.topicMetadataRefreshIntervalMs = topicMetadataRefreshIntervalMs;
    }

    public Integer getSendBufferBytes() {
        return sendBufferBytes;
    }

    public void setSendBufferBytes(Integer sendBufferBytes) {
        this.sendBufferBytes = sendBufferBytes;
    }

    public short getRequestRequiredAcks() {
        return requestRequiredAcks;
    }

    public void setRequestRequiredAcks(short requestRequiredAcks) {
        this.requestRequiredAcks = requestRequiredAcks;
    }

    public Integer getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public Integer getQueueBufferingMaxMs() {
        return queueBufferingMaxMs;
    }

    public void setQueueBufferingMaxMs(Integer queueBufferingMaxMs) {
        this.queueBufferingMaxMs = queueBufferingMaxMs;
    }

    public Integer getQueueBufferingMaxMessages() {
        return queueBufferingMaxMessages;
    }

    public void setQueueBufferingMaxMessages(Integer queueBufferingMaxMessages) {
        this.queueBufferingMaxMessages = queueBufferingMaxMessages;
    }

    public Integer getQueueEnqueueTimeoutMs() {
        return queueEnqueueTimeoutMs;
    }

    public void setQueueEnqueueTimeoutMs(Integer queueEnqueueTimeoutMs) {
        this.queueEnqueueTimeoutMs = queueEnqueueTimeoutMs;
    }

    public Integer getBatchNumMessages() {
        return batchNumMessages;
    }

    public void setBatchNumMessages(Integer batchNumMessages) {
        this.batchNumMessages = batchNumMessages;
    }

    public String getSerializerClass() {
        return serializerClass;
    }

    public void setSerializerClass(String serializerClass) {
        this.serializerClass = serializerClass;
    }

    public String getKeySerializerClass() {
        return keySerializerClass;
    }

    public void setKeySerializerClass(String keySerializerClass) {
        this.keySerializerClass = keySerializerClass;
    }
}
