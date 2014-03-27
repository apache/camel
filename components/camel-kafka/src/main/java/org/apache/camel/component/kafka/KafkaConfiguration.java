package org.apache.camel.component.kafka;

import kafka.consumer.ConsumerConfig;
import kafka.producer.DefaultPartitioner;

public class KafkaConfiguration {
    private String zookeeperHost;
    private int zookeeperPort=2181;
    private String topic;
    private String groupId;
    private String partitioner = DefaultPartitioner.class.getCanonicalName();
    private int consumerStreams = 10;

    //Common configuration properties
    private String clientId = null;

    //Consumer configuration properties
    private String consumerId = null;
    private int socketTimeoutMs = ConsumerConfig.SocketTimeout();
    private int socketReceiveBufferBytes = ConsumerConfig.SocketBufferSize();
    private int fetchMessageMaxBytes = ConsumerConfig.FetchSize();
    private boolean autoCommitEnable = ConsumerConfig.AutoCommit();
    private int autoCommitIntervalMs = ConsumerConfig.AutoCommitInterval();
    private int queuedMaxMessages = ConsumerConfig.MaxQueuedChunks();
    private int rebalanceMaxRetries = ConsumerConfig.MaxRebalanceRetries();
    private int fetchMinBytes = ConsumerConfig.MinFetchBytes();
    private int fetchWaitMaxMs = ConsumerConfig.MaxFetchWaitMs();
    private Integer rebalanceBackoffMs = null;
    private int refreshLeaderBackoffMs = ConsumerConfig.RefreshMetadataBackoffMs();
    private String autoOffsetReset = ConsumerConfig.AutoOffsetReset();
    private int consumerTimeoutMs = ConsumerConfig.ConsumerTimeoutMs();

    //Zookeepr configuration properties
    private int zookeeperSessionTimeoutMs = 6000;
    private Integer zookeeperConnectionTimeoutMs = null;
    private int zookeeperSyncTimeMs = 2000;

    //Producer configuration properties
    private String producerType = "sync";
    private String compressionCodec = "none";
    private String compressedTopics = null;
    private int messageSendMaxRetries = 3;
    private int retryBackoffMs = 100;
    private int topicMetadataRefreshIntervalMs = 600 * 1000;

    //Sync producer config
    private int sendBufferBytes = 100 * 1024;
    private short requestRequiredAcks = 0;
    private int requestTimeoutMs = 10000;

    //Async producer config
    private int queueBufferingMaxMs = 5000;
    private int queueBufferingMaxMessages = 10000;
    private int queueEnqueueTimeoutMs = -1;
    private int batchNumMessages = 200;
    private String serializerClass = kafka.serializer.DefaultEncoder.class.getCanonicalName();
    private String keySerializerClass = "";

    public KafkaConfiguration() {
    }

    public String getZookeeperHost() {
        return zookeeperHost;
    }

    public void setZookeeperHost(String zookeeperHost) {
        this.zookeeperHost = zookeeperHost;
    }

    public int getZookeeperPort() {
        return zookeeperPort;
    }

    public void setZookeeperPort(int zookeeperPort) {
        this.zookeeperPort = zookeeperPort;
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

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getSocketReceiveBufferBytes() {
        return socketReceiveBufferBytes;
    }

    public void setSocketReceiveBufferBytes(int socketReceiveBufferBytes) {
        this.socketReceiveBufferBytes = socketReceiveBufferBytes;
    }

    public int getFetchMessageMaxBytes() {
        return fetchMessageMaxBytes;
    }

    public void setFetchMessageMaxBytes(int fetchMessageMaxBytes) {
        this.fetchMessageMaxBytes = fetchMessageMaxBytes;
    }

    public boolean isAutoCommitEnable() {
        return autoCommitEnable;
    }

    public void setAutoCommitEnable(boolean autoCommitEnable) {
        this.autoCommitEnable = autoCommitEnable;
    }

    public int getAutoCommitIntervalMs() {
        return autoCommitIntervalMs;
    }

    public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
        this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public int getQueuedMaxMessages() {
        return queuedMaxMessages;
    }

    public void setQueuedMaxMessages(int queuedMaxMessages) {
        this.queuedMaxMessages = queuedMaxMessages;
    }

    public int getRebalanceMaxRetries() {
        return rebalanceMaxRetries;
    }

    public void setRebalanceMaxRetries(int rebalanceMaxRetries) {
        this.rebalanceMaxRetries = rebalanceMaxRetries;
    }

    public int getFetchMinBytes() {
        return fetchMinBytes;
    }

    public void setFetchMinBytes(int fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public int getFetchWaitMaxMs() {
        return fetchWaitMaxMs;
    }

    public void setFetchWaitMaxMs(int fetchWaitMaxMs) {
        this.fetchWaitMaxMs = fetchWaitMaxMs;
    }

    public Integer getRebalanceBackoffMs() {
        return rebalanceBackoffMs;
    }

    public void setRebalanceBackoffMs(Integer rebalanceBackoffMs) {
        this.rebalanceBackoffMs = rebalanceBackoffMs;
    }

    public int getRefreshLeaderBackoffMs() {
        return refreshLeaderBackoffMs;
    }

    public void setRefreshLeaderBackoffMs(int refreshLeaderBackoffMs) {
        this.refreshLeaderBackoffMs = refreshLeaderBackoffMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public int getConsumerTimeoutMs() {
        return consumerTimeoutMs;
    }

    public void setConsumerTimeoutMs(int consumerTimeoutMs) {
        this.consumerTimeoutMs = consumerTimeoutMs;
    }

    public int getZookeeperSessionTimeoutMs() {
        return zookeeperSessionTimeoutMs;
    }

    public void setZookeeperSessionTimeoutMs(int zookeeperSessionTimeoutMs) {
        this.zookeeperSessionTimeoutMs = zookeeperSessionTimeoutMs;
    }

    public Integer getZookeeperConnectionTimeoutMs() {
        return zookeeperConnectionTimeoutMs;
    }

    public void setZookeeperConnectionTimeoutMs(Integer zookeeperConnectionTimeoutMs) {
        this.zookeeperConnectionTimeoutMs = zookeeperConnectionTimeoutMs;
    }

    public int getZookeeperSyncTimeMs() {
        return zookeeperSyncTimeMs;
    }

    public void setZookeeperSyncTimeMs(int zookeeperSyncTimeMs) {
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

    public int getMessageSendMaxRetries() {
        return messageSendMaxRetries;
    }

    public void setMessageSendMaxRetries(int messageSendMaxRetries) {
        this.messageSendMaxRetries = messageSendMaxRetries;
    }

    public int getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(int retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getTopicMetadataRefreshIntervalMs() {
        return topicMetadataRefreshIntervalMs;
    }

    public void setTopicMetadataRefreshIntervalMs(int topicMetadataRefreshIntervalMs) {
        this.topicMetadataRefreshIntervalMs = topicMetadataRefreshIntervalMs;
    }

    public int getSendBufferBytes() {
        return sendBufferBytes;
    }

    public void setSendBufferBytes(int sendBufferBytes) {
        this.sendBufferBytes = sendBufferBytes;
    }

    public short getRequestRequiredAcks() {
        return requestRequiredAcks;
    }

    public void setRequestRequiredAcks(short requestRequiredAcks) {
        this.requestRequiredAcks = requestRequiredAcks;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getQueueBufferingMaxMs() {
        return queueBufferingMaxMs;
    }

    public void setQueueBufferingMaxMs(int queueBufferingMaxMs) {
        this.queueBufferingMaxMs = queueBufferingMaxMs;
    }

    public int getQueueBufferingMaxMessages() {
        return queueBufferingMaxMessages;
    }

    public void setQueueBufferingMaxMessages(int queueBufferingMaxMessages) {
        this.queueBufferingMaxMessages = queueBufferingMaxMessages;
    }

    public int getQueueEnqueueTimeoutMs() {
        return queueEnqueueTimeoutMs;
    }

    public void setQueueEnqueueTimeoutMs(int queueEnqueueTimeoutMs) {
        this.queueEnqueueTimeoutMs = queueEnqueueTimeoutMs;
    }

    public int getBatchNumMessages() {
        return batchNumMessages;
    }

    public void setBatchNumMessages(int batchNumMessages) {
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