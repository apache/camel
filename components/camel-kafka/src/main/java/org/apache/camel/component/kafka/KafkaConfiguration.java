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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class KafkaConfiguration {

    @UriParam
    private String zookeeperConnect;
    @UriParam
    private String zookeeperHost;
    @UriParam(defaultValue = "2181")
    private int zookeeperPort = 2181;
    @UriParam @Metadata(required = "true")
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
    @UriParam(label = "consumer", defaultValue = "zookeeper", enums = "zookeeper,kafka")
    private String offsetsStorage = "zookeeper";
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean dualCommitEnabled = true;

    //Zookeepr configuration properties
    @UriParam
    private Integer zookeeperSessionTimeoutMs;
    @UriParam
    private Integer zookeeperConnectionTimeoutMs;
    @UriParam
    private Integer zookeeperSyncTimeMs;

    //Producer configuration properties
    @UriPath
    private String brokers;
    @UriParam(label = "producer", defaultValue = "sync", enums = "async,sync")
    private String producerType = "sync";
    @UriParam(label = "producer", defaultValue = "none", enums = "none,gzip,snappy")
    private String compressionCodec = "none";
    @UriParam(label = "producer")
    private String compressedTopics;
    @UriParam(label = "producer", defaultValue = "3")
    private Integer messageSendMaxRetries = 3;
    @UriParam(label = "producer", defaultValue = "100")
    private Integer retryBackoffMs = 100;
    @UriParam(label = "producer", defaultValue = "600000")
    private Integer topicMetadataRefreshIntervalMs = 600 * 1000;

    //Sync producer config
    @UriParam(label = "producer", defaultValue = "" + 100 * 1024)
    private Integer sendBufferBytes = 100 * 1024;
    @UriParam(label = "producer", defaultValue = "0")
    private short requestRequiredAcks;
    @UriParam(label = "producer", defaultValue = "10000")
    private Integer requestTimeoutMs = 10000;

    //Async producer config
    @UriParam(label = "producer", defaultValue = "5000")
    private Integer queueBufferingMaxMs = 5000;
    @UriParam(label = "producer", defaultValue = "10000")
    private Integer queueBufferingMaxMessages = 10000;
    @UriParam(label = "producer")
    private Integer queueEnqueueTimeoutMs;
    @UriParam(label = "producer", defaultValue = "200")
    private Integer batchNumMessages = 200;
    @UriParam(label = "producer")
    private String serializerClass;
    @UriParam(label = "producer")
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
        addPropertyIfNotNull(props, "zookeeper.session.timeout.ms", getZookeeperSessionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.connection.timeout.ms", getZookeeperConnectionTimeoutMs());
        addPropertyIfNotNull(props, "zookeeper.sync.time.ms", getZookeeperSyncTimeMs());
        addPropertyIfNotNull(props, "offsets.storage", getOffsetsStorage());
        addPropertyIfNotNull(props, "dual.commit.enabled", isDualCommitEnabled());
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

    /**
     * The partitioner class for partitioning messages amongst sub-topics. The default partitioner is based on the hash of the key.
     */
    public void setPartitioner(String partitioner) {
        this.partitioner = partitioner;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * Name of the topic to use
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getConsumerStreams() {
        return consumerStreams;
    }

    /**
     * Number of concurrent consumers on the consumer
     */
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

    /**
     * Max number of message chunks buffered for consumption. Each chunk can be up to fetch.message.max.bytes.
     */
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

    public String getBrokers() {
        return brokers;
    }

    /**
     * This is for bootstrapping and the producer will only use it for getting metadata (topics, partitions and replicas).
     * The socket connections for sending the actual data will be established based on the broker information returned in the metadata.
     * The format is host1:port1,host2:port2, and the list can be a subset of brokers or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>metadata.broker.list</tt> in the Kafka documentation.
     */
    public void setBrokers(String brokers) {
        this.brokers = brokers;
    }

    public String getProducerType() {
        return producerType;
    }

    /**
     * This parameter specifies whether the messages are sent asynchronously in a background thread.
     * Valid values are (1) async for asynchronous send and (2) sync for synchronous send.
     * By setting the producer to async we allow batching together of requests (which is great for throughput)
     * but open the possibility of a failure of the client machine dropping unsent data.
     */
    public void setProducerType(String producerType) {
        this.producerType = producerType;
    }

    public String getCompressionCodec() {
        return compressionCodec;
    }

    /**
     * This parameter allows you to specify the compression codec for all data generated by this producer. Valid values are "none", "gzip" and "snappy".
     */
    public void setCompressionCodec(String compressionCodec) {
        this.compressionCodec = compressionCodec;
    }

    public String getCompressedTopics() {
        return compressedTopics;
    }

    /**
     * This parameter allows you to set whether compression should be turned on for particular topics.
     * If the compression codec is anything other than NoCompressionCodec, enable compression only for specified topics if any.
     * If the list of compressed topics is empty, then enable the specified compression codec for all topics.
     * If the compression codec is NoCompressionCodec, compression is disabled for all topics
     */
    public void setCompressedTopics(String compressedTopics) {
        this.compressedTopics = compressedTopics;
    }

    public Integer getMessageSendMaxRetries() {
        return messageSendMaxRetries;
    }

    /**
     * This property will cause the producer to automatically retry a failed send request.
     * This property specifies the number of retries when such failures occur. Note that setting a non-zero value here
     * can lead to duplicates in the case of network errors that cause a message to be sent but the acknowledgement to be lost.
     */
    public void setMessageSendMaxRetries(Integer messageSendMaxRetries) {
        this.messageSendMaxRetries = messageSendMaxRetries;
    }

    public Integer getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * Before each retry, the producer refreshes the metadata of relevant topics to see if a new leader has been elected.
     * Since leader election takes a bit of time, this property specifies the amount of time that the producer waits before refreshing the metadata.
     */
    public void setRetryBackoffMs(Integer retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public Integer getTopicMetadataRefreshIntervalMs() {
        return topicMetadataRefreshIntervalMs;
    }

    /**
     * The producer generally refreshes the topic metadata from brokers when there is a failure (partition missing,
     * leader not available...). It will also poll regularly (default: every 10min so 600000ms).
     * If you set this to a negative value, metadata will only get refreshed on failure.
     * If you set this to zero, the metadata will get refreshed after each message sent (not recommended).
     * Important note: the refresh happen only AFTER the message is sent, so if the producer never
     * sends a message the metadata is never refreshed
     */
    public void setTopicMetadataRefreshIntervalMs(Integer topicMetadataRefreshIntervalMs) {
        this.topicMetadataRefreshIntervalMs = topicMetadataRefreshIntervalMs;
    }

    public Integer getSendBufferBytes() {
        return sendBufferBytes;
    }

    /**
     * Socket write buffer size
     */
    public void setSendBufferBytes(Integer sendBufferBytes) {
        this.sendBufferBytes = sendBufferBytes;
    }

    public short getRequestRequiredAcks() {
        return requestRequiredAcks;
    }

    /**
     * This value controls when a produce request is considered completed. Specifically,
     * how many other brokers must have committed the data to their log and acknowledged this to the leader?
     * Typical values are (0, 1 or -1):
     * 0, which means that the producer never waits for an acknowledgement from the broker (the same behavior as 0.7).
     * This option provides the lowest latency but the weakest durability guarantees (some data will be lost when a server fails).
     * 1, which means that the producer gets an acknowledgement after the leader replica has received the data.
     * This option provides better durability as the client waits until the server acknowledges the request as successful
     * (only messages that were written to the now-dead leader but not yet replicated will be lost).
     * -1, The producer gets an acknowledgement after all in-sync replicas have received the data.
     * This option provides the greatest level of durability.
     * However, it does not completely eliminate the risk of message loss because the number of in sync replicas may,
     * in rare cases, shrink to 1. If you want to ensure that some minimum number of replicas
     * (typically a majority) receive a write, then you must set the topic-level min.insync.replicas setting.
     * Please read the Replication section of the design documentation for a more in-depth discussion.
     */
    public void setRequestRequiredAcks(short requestRequiredAcks) {
        this.requestRequiredAcks = requestRequiredAcks;
    }

    public Integer getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * The amount of time the broker will wait trying to meet the request.required.acks requirement before sending back an error to the client.
     */
    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public Integer getQueueBufferingMaxMs() {
        return queueBufferingMaxMs;
    }

    /**
     * Maximum time to buffer data when using async mode.
     * For example a setting of 100 will try to batch together 100ms of messages to send at once.
     * This will improve throughput but adds message delivery latency due to the buffering.
     */
    public void setQueueBufferingMaxMs(Integer queueBufferingMaxMs) {
        this.queueBufferingMaxMs = queueBufferingMaxMs;
    }

    public Integer getQueueBufferingMaxMessages() {
        return queueBufferingMaxMessages;
    }

    /**
     * The maximum number of unsent messages that can be queued up the producer when using async
     * mode before either the producer must be blocked or data must be dropped.
     */
    public void setQueueBufferingMaxMessages(Integer queueBufferingMaxMessages) {
        this.queueBufferingMaxMessages = queueBufferingMaxMessages;
    }

    public Integer getQueueEnqueueTimeoutMs() {
        return queueEnqueueTimeoutMs;
    }

    /**
     * The amount of time to block before dropping messages when running in async mode and the buffer has reached
     * queue.buffering.max.messages. If set to 0 events will be enqueued immediately or dropped if the queue is full
     * (the producer send call will never block). If set to -1 the producer will block indefinitely and never willingly drop a send.
     */
    public void setQueueEnqueueTimeoutMs(Integer queueEnqueueTimeoutMs) {
        this.queueEnqueueTimeoutMs = queueEnqueueTimeoutMs;
    }

    public Integer getBatchNumMessages() {
        return batchNumMessages;
    }

    /**
     * The number of messages to send in one batch when using async mode.
     * The producer will wait until either this number of messages are ready to send or queue.buffer.max.ms is reached.
     */
    public void setBatchNumMessages(Integer batchNumMessages) {
        this.batchNumMessages = batchNumMessages;
    }

    public String getSerializerClass() {
        return serializerClass;
    }

    /**
     * The serializer class for messages. The default encoder takes a byte[] and returns the same byte[].
     * The default class is kafka.serializer.DefaultEncoder
     */
    public void setSerializerClass(String serializerClass) {
        this.serializerClass = serializerClass;
    }

    public String getKeySerializerClass() {
        return keySerializerClass;
    }

    /**
     * The serializer class for keys (defaults to the same as for messages if nothing is given).
     */
    public void setKeySerializerClass(String keySerializerClass) {
        this.keySerializerClass = keySerializerClass;
    }

    public String getOffsetsStorage() {
        return offsetsStorage;
    }

    /**
     * Select where offsets should be stored (zookeeper or kafka).
     */
    public void setOffsetsStorage(String offsetsStorage) {
        this.offsetsStorage = offsetsStorage;
    }

    public Boolean isDualCommitEnabled() {
        return dualCommitEnabled;
    }

    /**
     * If you are using "kafka" as offsets.storage, you can dual commit offsets to ZooKeeper (in addition to Kafka).
     * This is required during migration from zookeeper-based offset storage to kafka-based offset storage.
     * With respect to any given consumer group, it is safe to turn this off after all instances within that group have been migrated
     * to the new version that commits offsets to the broker (instead of directly to ZooKeeper).
     */
    public void setDualCommitEnabled(Boolean dualCommitEnabled) {
        this.dualCommitEnabled = dualCommitEnabled;
    }
}
