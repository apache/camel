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

import java.util.concurrent.ExecutorService;

import kafka.message.MessageAndMetadata;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(scheme = "kafka", title = "Kafka", syntax = "kafka:brokers", consumerClass = KafkaConsumer.class, label = "messaging")
public class KafkaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriParam
    private KafkaConfiguration configuration = new KafkaConfiguration();

    @UriParam(description = "If the option is true, then KafkaProducer will ignore the KafkaConstants.TOPIC header setting of the inbound message.", defaultValue = "false")
    private boolean bridgeEndpoint;

    public KafkaEndpoint() {
    }

    public KafkaEndpoint(String endpointUri, KafkaComponent component) {
        super(endpointUri, component);
    }

    public KafkaConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = createConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    protected KafkaConfiguration createConfiguration() {
        return new KafkaConfiguration();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KafkaConsumer consumer = new KafkaConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        String msgClassName = getConfiguration().getSerializerClass();
        String keyClassName = getConfiguration().getKeySerializerClass();
        if (msgClassName == null) {
            msgClassName = KafkaConstants.KAFKA_DEFAULT_ENCODER;
        }
        if (keyClassName == null) {
            keyClassName = msgClassName;
        }

        ClassLoader cl = getClass().getClassLoader();

        Class<?> k;
        try {
            k = cl.loadClass(keyClassName);
        } catch (ClassNotFoundException x) {
            k = getCamelContext().getClassResolver().resolveMandatoryClass(keyClassName);
        }
        Class<?> v;
        try {
            v = cl.loadClass(msgClassName);
        } catch (ClassNotFoundException x) {
            v = getCamelContext().getClassResolver().resolveMandatoryClass(msgClassName);
        }
        return createProducer(k, v, this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "KafkaTopic[" + configuration.getTopic() + "]", configuration.getConsumerStreams());
    }

    public Exchange createKafkaExchange(MessageAndMetadata<byte[], byte[]> mm) {
        Exchange exchange = super.createExchange();

        Message message = exchange.getIn();
        message.setHeader(KafkaConstants.PARTITION, mm.partition());
        message.setHeader(KafkaConstants.TOPIC, mm.topic());
        message.setHeader(KafkaConstants.OFFSET, mm.offset());
        if (mm.key() != null) {
            message.setHeader(KafkaConstants.KEY, new String(mm.key()));
        }
        message.setBody(mm.message());

        return exchange;
    }

    protected <K, V> KafkaProducer<K, V> createProducer(Class<K> keyClass, Class<V> valueClass, KafkaEndpoint endpoint) {
        return new KafkaProducer<K, V>(endpoint);
    }

    // Delegated properties from the configuration
    //-------------------------------------------------------------------------

    public String getZookeeperConnect() {
        return configuration.getZookeeperConnect();
    }

    public void setZookeeperConnect(String zookeeperConnect) {
        configuration.setZookeeperConnect(zookeeperConnect);
    }

    public String getZookeeperHost() {
        return configuration.getZookeeperHost();
    }

    public void setZookeeperHost(String zookeeperHost) {
        configuration.setZookeeperHost(zookeeperHost);
    }

    public int getZookeeperPort() {
        return configuration.getZookeeperPort();
    }

    public void setZookeeperPort(int zookeeperPort) {
        configuration.setZookeeperPort(zookeeperPort);
    }

    public String getGroupId() {
        return configuration.getGroupId();
    }

    public void setGroupId(String groupId) {
        configuration.setGroupId(groupId);
    }

    public String getPartitioner() {
        return configuration.getPartitioner();
    }

    public void setPartitioner(String partitioner) {
        configuration.setPartitioner(partitioner);
    }

    public String getTopic() {
        return configuration.getTopic();
    }

    public void setTopic(String topic) {
        configuration.setTopic(topic);
    }

    public String getBrokers() {
        return configuration.getBrokers();
    }

    public void setBrokers(String brokers) {
        configuration.setBrokers(brokers);
    }

    public int getConsumerStreams() {
        return configuration.getConsumerStreams();
    }

    public void setConsumerStreams(int consumerStreams) {
        configuration.setConsumerStreams(consumerStreams);
    }

    public int getBatchSize() {
        return configuration.getBatchSize();
    }

    public void setBatchSize(int batchSize) {
        this.configuration.setBatchSize(batchSize);
    }

    public int getBarrierAwaitTimeoutMs() {
        return configuration.getBarrierAwaitTimeoutMs();
    }

    public void setBarrierAwaitTimeoutMs(int barrierAwaitTimeoutMs) {
        this.configuration.setBarrierAwaitTimeoutMs(barrierAwaitTimeoutMs);
    }

    public int getConsumersCount() {
        return this.configuration.getConsumersCount();
    }

    public void setConsumersCount(int consumersCount) {
        this.configuration.setConsumersCount(consumersCount);
    }

    public void setConsumerTimeoutMs(int consumerTimeoutMs) {
        configuration.setConsumerTimeoutMs(consumerTimeoutMs);
    }

    public void setSerializerClass(String serializerClass) {
        configuration.setSerializerClass(serializerClass);
    }

    public void setQueueBufferingMaxMessages(int queueBufferingMaxMessages) {
        configuration.setQueueBufferingMaxMessages(queueBufferingMaxMessages);
    }

    public int getFetchWaitMaxMs() {
        return configuration.getFetchWaitMaxMs();
    }

    public Integer getZookeeperConnectionTimeoutMs() {
        return configuration.getZookeeperConnectionTimeoutMs();
    }

    public void setZookeeperConnectionTimeoutMs(Integer zookeeperConnectionTimeoutMs) {
        configuration.setZookeeperConnectionTimeoutMs(zookeeperConnectionTimeoutMs);
    }

    public void setMessageSendMaxRetries(int messageSendMaxRetries) {
        configuration.setMessageSendMaxRetries(messageSendMaxRetries);
    }

    public int getQueueBufferingMaxMs() {
        return configuration.getQueueBufferingMaxMs();
    }

    public void setRequestRequiredAcks(short requestRequiredAcks) {
        configuration.setRequestRequiredAcks(requestRequiredAcks);
    }

    public Integer getRebalanceBackoffMs() {
        return configuration.getRebalanceBackoffMs();
    }

    public void setQueueEnqueueTimeoutMs(int queueEnqueueTimeoutMs) {
        configuration.setQueueEnqueueTimeoutMs(queueEnqueueTimeoutMs);
    }

    public int getFetchMessageMaxBytes() {
        return configuration.getFetchMessageMaxBytes();
    }

    public int getQueuedMaxMessages() {
        return configuration.getQueuedMaxMessageChunks();
    }

    public int getAutoCommitIntervalMs() {
        return configuration.getAutoCommitIntervalMs();
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        configuration.setSocketTimeoutMs(socketTimeoutMs);
    }

    public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
        configuration.setAutoCommitIntervalMs(autoCommitIntervalMs);
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        configuration.setRequestTimeoutMs(requestTimeoutMs);
    }

    public void setCompressedTopics(String compressedTopics) {
        configuration.setCompressedTopics(compressedTopics);
    }

    public int getSocketReceiveBufferBytes() {
        return configuration.getSocketReceiveBufferBytes();
    }

    public void setSendBufferBytes(int sendBufferBytes) {
        configuration.setSendBufferBytes(sendBufferBytes);
    }

    public void setFetchMessageMaxBytes(int fetchMessageMaxBytes) {
        configuration.setFetchMessageMaxBytes(fetchMessageMaxBytes);
    }

    public int getRefreshLeaderBackoffMs() {
        return configuration.getRefreshLeaderBackoffMs();
    }

    public void setFetchWaitMaxMs(int fetchWaitMaxMs) {
        configuration.setFetchWaitMaxMs(fetchWaitMaxMs);
    }

    public int getTopicMetadataRefreshIntervalMs() {
        return configuration.getTopicMetadataRefreshIntervalMs();
    }

    public void setZookeeperSessionTimeoutMs(int zookeeperSessionTimeoutMs) {
        configuration.setZookeeperSessionTimeoutMs(zookeeperSessionTimeoutMs);
    }

    public Integer getConsumerTimeoutMs() {
        return configuration.getConsumerTimeoutMs();
    }

    public void setAutoCommitEnable(boolean autoCommitEnable) {
        configuration.setAutoCommitEnable(autoCommitEnable);
    }

    public String getCompressionCodec() {
        return configuration.getCompressionCodec();
    }

    public void setProducerType(String producerType) {
        configuration.setProducerType(producerType);
    }

    public String getClientId() {
        return configuration.getClientId();
    }

    public int getFetchMinBytes() {
        return configuration.getFetchMinBytes();
    }

    public String getAutoOffsetReset() {
        return configuration.getAutoOffsetReset();
    }

    public void setRefreshLeaderBackoffMs(int refreshLeaderBackoffMs) {
        configuration.setRefreshLeaderBackoffMs(refreshLeaderBackoffMs);
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        configuration.setAutoOffsetReset(autoOffsetReset);
    }

    public void setConsumerId(String consumerId) {
        configuration.setConsumerId(consumerId);
    }

    public int getRetryBackoffMs() {
        return configuration.getRetryBackoffMs();
    }

    public int getRebalanceMaxRetries() {
        return configuration.getRebalanceMaxRetries();
    }

    public Boolean isAutoCommitEnable() {
        return configuration.isAutoCommitEnable();
    }

    public void setQueueBufferingMaxMs(int queueBufferingMaxMs) {
        configuration.setQueueBufferingMaxMs(queueBufferingMaxMs);
    }

    public void setRebalanceMaxRetries(int rebalanceMaxRetries) {
        configuration.setRebalanceMaxRetries(rebalanceMaxRetries);
    }

    public int getZookeeperSessionTimeoutMs() {
        return configuration.getZookeeperSessionTimeoutMs();
    }

    public void setKeySerializerClass(String keySerializerClass) {
        configuration.setKeySerializerClass(keySerializerClass);
    }

    public void setCompressionCodec(String compressionCodec) {
        configuration.setCompressionCodec(compressionCodec);
    }

    public void setClientId(String clientId) {
        configuration.setClientId(clientId);
    }

    public int getSocketTimeoutMs() {
        return configuration.getSocketTimeoutMs();
    }

    public String getCompressedTopics() {
        return configuration.getCompressedTopics();
    }

    public int getZookeeperSyncTimeMs() {
        return configuration.getZookeeperSyncTimeMs();
    }

    public void setSocketReceiveBufferBytes(int socketReceiveBufferBytes) {
        configuration.setSocketReceiveBufferBytes(socketReceiveBufferBytes);
    }

    public int getQueueEnqueueTimeoutMs() {
        return configuration.getQueueEnqueueTimeoutMs();
    }

    public int getQueueBufferingMaxMessages() {
        return configuration.getQueueBufferingMaxMessages();
    }

    public void setZookeeperSyncTimeMs(int zookeeperSyncTimeMs) {
        configuration.setZookeeperSyncTimeMs(zookeeperSyncTimeMs);
    }

    public String getKeySerializerClass() {
        return configuration.getKeySerializerClass();
    }

    public void setTopicMetadataRefreshIntervalMs(int topicMetadataRefreshIntervalMs) {
        configuration.setTopicMetadataRefreshIntervalMs(topicMetadataRefreshIntervalMs);
    }

    public void setBatchNumMessages(int batchNumMessages) {
        configuration.setBatchNumMessages(batchNumMessages);
    }

    public int getSendBufferBytes() {
        return configuration.getSendBufferBytes();
    }

    public void setRebalanceBackoffMs(Integer rebalanceBackoffMs) {
        configuration.setRebalanceBackoffMs(rebalanceBackoffMs);
    }

    public void setQueuedMaxMessages(int queuedMaxMessages) {
        configuration.setQueuedMaxMessageChunks(queuedMaxMessages);
    }

    public void setRetryBackoffMs(int retryBackoffMs) {
        configuration.setRetryBackoffMs(retryBackoffMs);
    }

    public int getBatchNumMessages() {
        return configuration.getBatchNumMessages();
    }

    public short getRequestRequiredAcks() {
        return configuration.getRequestRequiredAcks();
    }

    public String getProducerType() {
        return configuration.getProducerType();
    }

    public String getConsumerId() {
        return configuration.getConsumerId();
    }

    public int getMessageSendMaxRetries() {
        return configuration.getMessageSendMaxRetries();
    }

    public void setFetchMinBytes(int fetchMinBytes) {
        configuration.setFetchMinBytes(fetchMinBytes);
    }

    public String getSerializerClass() {
        return configuration.getSerializerClass();
    }

    public int getRequestTimeoutMs() {
        return configuration.getRequestTimeoutMs();
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public String getOffsetsStorage() {
        return configuration.getOffsetsStorage();
    }

    public void setOffsetsStorage(String offsetsStorage) {
        configuration.setOffsetsStorage(offsetsStorage);
    }

    public Boolean isDualCommitEnabled() {
        return configuration.isDualCommitEnabled();
    }

    public void setDualCommitEnabled(boolean dualCommitEnabled) {
        configuration.setDualCommitEnabled(dualCommitEnabled);
    }

}
