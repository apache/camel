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

    @UriParam(label = "consumer", defaultValue = "10")
    private int consumerStreams = 10;
    @UriParam(label = "consumer", defaultValue = "1")
    private int consumersCount = 1;
    @UriParam(label = "consumer", defaultValue = "100")
    private int batchSize = 100;
    @UriParam(label = "consumer", defaultValue = "10000")
    private int barrierAwaitTimeoutMs = 10000;

    //Common configuration properties
    @UriParam
    private String clientId;

    //Consumer configuration properties
    @UriParam(label = "consumer")
    private String consumerId;
    @UriParam(label = "consumer", defaultValue = "30000")
    private Integer socketTimeoutMs = 30 * 1000;
    @UriParam(label = "consumer", defaultValue = "" + 64 * 1024)
    private Integer socketReceiveBufferBytes = 64 * 1024;
    @UriParam(label = "consumer", defaultValue = "" + 1024 * 1024)
    private Integer fetchMessageMaxBytes = 1024 * 1024;
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean autoCommitEnable = true;
    @UriParam(label = "consumer", defaultValue = "60000")
    private Integer autoCommitIntervalMs = 60 * 1000;
    @UriParam(label = "consumer", defaultValue = "2")
    private Integer queuedMaxMessageChunks = 2;
    @UriParam(label = "consumer", defaultValue = "4")
    private Integer rebalanceMaxRetries = 4;
    @UriParam(label = "consumer", defaultValue = "1")
    private Integer fetchMinBytes = 1;
    @UriParam(label = "consumer", defaultValue = "100")
    private Integer fetchWaitMaxMs = 100;
    @UriParam(label = "consumer", defaultValue = "2000")
    private Integer rebalanceBackoffMs = 2000;
    @UriParam(label = "consumer", defaultValue = "200")
    private Integer refreshLeaderBackoffMs = 200;
    @UriParam(label = "consumer", defaultValue = "largest", enums = "smallest,largest,fail")
    private String autoOffsetReset = "largest";
    @UriParam(label = "consumer")
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
        addPropertyIfNotNull(props, "queued.max.message.chunks", getQueuedMaxMessageChunks());
        addPropertyIfNotNull(props, "rebalance.max.retries", getRebalanceMaxRetries());
        addPropertyIfNotNull(props, "rebalance.backoff.ms", getRebalanceBackoffMs());
        addPropertyIfNotNull(props, "refresh.leader.backoff.ms", getRefreshLeaderBackoffMs());
        addPropertyIfNotNull(props, "auto.offset.reset", getAutoOffsetReset());
        addPropertyIfNotNull(props, "consumer.timeout.ms", getConsumerTimeoutMs());
        addPropertyIfNotNull(props, "client.id", getClientId());
        addPropertyIfNotNull(props, "zookeeper.session.timeout.ms ", getZookeeperSessionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.connection.timeout.ms", getZookeeperConnectionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.sync.time.ms ", getZookeeperSyncTimeMs());
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

    /**
     * Specifies the ZooKeeper connection string in the form hostname:port where host and port are the host and port of a ZooKeeper server.
     * To allow connecting through other ZooKeeper nodes when that ZooKeeper machine is down you can also specify multiple hosts in the
     * form hostname1:port1,hostname2:port2,hostname3:port3.
     * The server may also have a ZooKeeper chroot path as part of it's ZooKeeper connection string which puts its data
     * under some path in the global ZooKeeper namespace. If so the consumer should use the same chroot path in its connection string.
     * For example to give a chroot path of /chroot/path you would give the connection
     * string as hostname1:port1,hostname2:port2,hostname3:port3/chroot/path.
     */
    public void setZookeeperConnect(String zookeeperConnect) {
        this.zookeeperConnect = zookeeperConnect;
        
        // connect overrides host and port
        this.zookeeperHost = null;
        this.zookeeperPort = -1;
    }

    public String getZookeeperHost() {
        return zookeeperHost;
    }

    /**
     * The zookeeper host to use.
     * <p/>
     * To connect to multiple zookeeper hosts use the zookeeperConnect option instead.
     * <p/>
     * This option can only be used if zookeeperConnect is not in use.
     */
    public void setZookeeperHost(String zookeeperHost) {
        if (this.zookeeperConnect == null) {
            this.zookeeperHost = zookeeperHost;
        }
    }

    public int getZookeeperPort() {
        return zookeeperPort;
    }

    /**
     * The zookeeper port to use
     * <p/>
     * To connect to multiple zookeeper hosts use the zookeeperConnect option instead.
     * <p/>
     * This option can only be used if zookeeperConnect is not in use.
     */
    public void setZookeeperPort(int zookeeperPort) {
        if (this.zookeeperConnect == null) {
            this.zookeeperPort = zookeeperPort;
        }
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * A string that uniquely identifies the group of consumer processes to which this consumer belongs.
     * By setting the same group id multiple processes indicate that they are all part of the same consumer group.
     */
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

    /**
     * The batchSize that the BatchingConsumerTask processes once.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBarrierAwaitTimeoutMs() {
        return barrierAwaitTimeoutMs;
    }

    /**
     * If the BatchingConsumerTask processes exchange exceed the batchSize, it will wait for barrierAwaitTimeoutMs.
     */
    public void setBarrierAwaitTimeoutMs(int barrierAwaitTimeoutMs) {
        this.barrierAwaitTimeoutMs = barrierAwaitTimeoutMs;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    /**
     * The number of consumers that connect to kafka server
     */
    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id is a user-specified string sent in each request to help trace calls.
     * It should logically identify the application making the request.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Generated automatically if not set.
     */
    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public Integer getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    /**
     * The socket timeout for network requests. The actual timeout set will be max.fetch.wait + socket.timeout.ms.
     */
    public void setSocketTimeoutMs(Integer socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public Integer getSocketReceiveBufferBytes() {
        return socketReceiveBufferBytes;
    }

    /**
     * The socket receive buffer for network requests
     */
    public void setSocketReceiveBufferBytes(Integer socketReceiveBufferBytes) {
        this.socketReceiveBufferBytes = socketReceiveBufferBytes;
    }

    public Integer getFetchMessageMaxBytes() {
        return fetchMessageMaxBytes;
    }

    /**
     * The number of byes of messages to attempt to fetch for each topic-partition in each fetch request.
     * These bytes will be read into memory for each partition, so this helps control the memory used by the consumer.
     * The fetch request size must be at least as large as the maximum message size the server allows or else it
     * is possible for the producer to send messages larger than the consumer can fetch.
     */
    public void setFetchMessageMaxBytes(Integer fetchMessageMaxBytes) {
        this.fetchMessageMaxBytes = fetchMessageMaxBytes;
    }

    public Boolean isAutoCommitEnable() {
        return autoCommitEnable;
    }

    /**
     * If true, periodically commit to ZooKeeper the offset of messages already fetched by the consumer.
     * This committed offset will be used when the process fails as the position from which the new consumer will begin.
     */
    public void setAutoCommitEnable(Boolean autoCommitEnable) {
        this.autoCommitEnable = autoCommitEnable;
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

    public Integer getQueuedMaxMessageChunks() {
        return queuedMaxMessageChunks;
    }

    public void setQueuedMaxMessageChunks(Integer queuedMaxMessageChunks) {
        this.queuedMaxMessageChunks = queuedMaxMessageChunks;
    }

    public Integer getRebalanceMaxRetries() {
        return rebalanceMaxRetries;
    }

    /**
     * When a new consumer joins a consumer group the set of consumers attempt to "rebalance" the load to assign partitions to each consumer.
     * If the set of consumers changes while this assignment is taking place the rebalance will fail and retry.
     * This setting controls the maximum number of attempts before giving up.
     */
    public void setRebalanceMaxRetries(Integer rebalanceMaxRetries) {
        this.rebalanceMaxRetries = rebalanceMaxRetries;
    }

    public Integer getFetchMinBytes() {
        return fetchMinBytes;
    }

    /**
     * The minimum amount of data the server should return for a fetch request.
     * If insufficient data is available the request will wait for that much data to accumulate before answering the request.
     */
    public void setFetchMinBytes(Integer fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public Integer getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    /**
     * The maximum amount of time the server will block before answering the fetch request if there isn't sufficient data to immediately satisfy fetch.min.bytes
     */
    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public Integer getRebalanceBackoffMs() {
        return rebalanceBackoffMs;
    }

    /**
     * Backoff time between retries during rebalance.
     */
    public void setRebalanceBackoffMs(Integer rebalanceBackoffMs) {
        this.rebalanceBackoffMs = rebalanceBackoffMs;
    }

    public Integer getRefreshLeaderBackoffMs() {
        return refreshLeaderBackoffMs;
    }

    /**
     * Backoff time to wait before trying to determine the leader of a partition that has just lost its leader.
     */
    public void setRefreshLeaderBackoffMs(Integer refreshLeaderBackoffMs) {
        this.refreshLeaderBackoffMs = refreshLeaderBackoffMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    /**
     * What to do when there is no initial offset in ZooKeeper or if an offset is out of range:
     * smallest : automatically reset the offset to the smallest offset
     * largest : automatically reset the offset to the largest offset
     * fail: throw exception to the consumer
     */
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public Integer getConsumerTimeoutMs() {
        return consumerTimeoutMs;
    }

    /**
     * Throw a timeout exception to the consumer if no message is available for consumption after the specified interval
     */
    public void setConsumerTimeoutMs(Integer consumerTimeoutMs) {
        this.consumerTimeoutMs = consumerTimeoutMs;
    }

    public Integer getZookeeperSessionTimeoutMs() {
        return zookeeperSessionTimeoutMs;
    }

    /**
     * ZooKeeper session timeout. If the consumer fails to heartbeat to ZooKeeper for this period of time it is considered dead and a rebalance will occur.
     */
    public void setZookeeperSessionTimeoutMs(Integer zookeeperSessionTimeoutMs) {
        this.zookeeperSessionTimeoutMs = zookeeperSessionTimeoutMs;
    }

    public Integer getZookeeperConnectionTimeoutMs() {
        return zookeeperConnectionTimeoutMs;
    }

    /**
     * The max time that the client waits while establishing a connection to zookeeper.
     */
    public void setZookeeperConnectionTimeoutMs(Integer zookeeperConnectionTimeoutMs) {
        this.zookeeperConnectionTimeoutMs = zookeeperConnectionTimeoutMs;
    }

    public Integer getZookeeperSyncTimeMs() {
        return zookeeperSyncTimeMs;
    }

    /**
     * How far a ZK follower can be behind a ZK leader
     */
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
