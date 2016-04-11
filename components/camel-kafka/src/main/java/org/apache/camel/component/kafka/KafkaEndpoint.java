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
import java.util.concurrent.ExecutorService;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * The kafka component allows messages to be sent to (or consumed from) Apache Kafka brokers.
 */
@UriEndpoint(scheme = "kafka", title = "Kafka", syntax = "kafka:brokers", consumerClass = KafkaConsumer.class, label = "messaging")
public class KafkaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriParam
    private KafkaConfiguration configuration = new KafkaConfiguration();
    @UriParam
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
        return createProducer(this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "KafkaTopic[" + configuration.getTopic() + "]", configuration.getConsumerStreams());
    }

    public Exchange createKafkaExchange(ConsumerRecord record) {
        Exchange exchange = super.createExchange();

        Message message = exchange.getIn();
        message.setHeader(KafkaConstants.PARTITION, record.partition());
        message.setHeader(KafkaConstants.TOPIC, record.topic());
        message.setHeader(KafkaConstants.OFFSET, record.offset());
        if (record.key() != null) {
            message.setHeader(KafkaConstants.KEY, record.key());
        }
        message.setBody(record.value());

        return exchange;
    }

    protected KafkaProducer createProducer(KafkaEndpoint endpoint) {
        return new KafkaProducer(endpoint);
    }

    // Delegated properties from the configuration
    //-------------------------------------------------------------------------

    public Properties createProducerProperties() {
        return configuration.createProducerProperties();
    }

    public void setValueDeserializer(String valueDeserializer) {
        configuration.setValueDeserializer(valueDeserializer);
    }

    public void setRequestTimeoutMs(Integer requestTimeoutMs) {
        configuration.setRequestTimeoutMs(requestTimeoutMs);
    }

    public void setProducerBatchSize(Integer producerBatchSize) {
        configuration.setProducerBatchSize(producerBatchSize);
    }

    public void setRetryBackoffMs(Integer retryBackoffMs) {
        configuration.setRetryBackoffMs(retryBackoffMs);
    }

    public void setNoOfMetricsSample(Integer noOfMetricsSample) {
        configuration.setNoOfMetricsSample(noOfMetricsSample);
    }

    public String getMetricReporters() {
        return configuration.getMetricReporters();
    }

    public void setSslKeystoreType(String sslKeystoreType) {
        configuration.setSslKeystoreType(sslKeystoreType);
    }

    public void setSslCipherSuites(String sslCipherSuites) {
        configuration.setSslCipherSuites(sslCipherSuites);
    }

    public void setClientId(String clientId) {
        configuration.setClientId(clientId);
    }

    public void setMetricsSampleWindowMs(Integer metricsSampleWindowMs) {
        configuration.setMetricsSampleWindowMs(metricsSampleWindowMs);
    }

    public String getKeyDeserializer() {
        return configuration.getKeyDeserializer();
    }

    public int getConsumersCount() {
        return configuration.getConsumersCount();
    }

    public String getSslKeyPassword() {
        return configuration.getSslKeyPassword();
    }

    public void setSendBufferBytes(Integer sendBufferBytes) {
        configuration.setSendBufferBytes(sendBufferBytes);
    }

    public Boolean isAutoCommitEnable() {
        return configuration.isAutoCommitEnable();
    }

    public Integer getMaxBlockMs() {
        return configuration.getMaxBlockMs();
    }

    public String getConsumerId() {
        return configuration.getConsumerId();
    }

    public void setSslProtocol(String sslProtocol) {
        configuration.setSslProtocol(sslProtocol);
    }

    public void setReceiveBufferBytes(Integer receiveBufferBytes) {
        configuration.setReceiveBufferBytes(receiveBufferBytes);
    }

    public Boolean getCheckCrcs() {
        return configuration.getCheckCrcs();
    }

    public void setGroupId(String groupId) {
        configuration.setGroupId(groupId);
    }

    public String getCompressionCodec() {
        return configuration.getCompressionCodec();
    }

    public String getGroupId() {
        return configuration.getGroupId();
    }

    public void setSslTruststoreLocation(String sslTruststoreLocation) {
        configuration.setSslTruststoreLocation(sslTruststoreLocation);
    }

    public String getKerberosInitCmd() {
        return configuration.getKerberosInitCmd();
    }

    public String getAutoOffsetReset() {
        return configuration.getAutoOffsetReset();
    }

    public void setAutoCommitEnable(Boolean autoCommitEnable) {
        configuration.setAutoCommitEnable(autoCommitEnable);
    }

    public void setSerializerClass(String serializerClass) {
        configuration.setSerializerClass(serializerClass);
    }

    public Integer getQueueBufferingMaxMessages() {
        return configuration.getQueueBufferingMaxMessages();
    }

    public void setSslEndpointAlgorithm(String sslEndpointAlgorithm) {
        configuration.setSslEndpointAlgorithm(sslEndpointAlgorithm);
    }

    public void setRetries(Integer retries) {
        configuration.setRetries(retries);
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        configuration.setAutoOffsetReset(autoOffsetReset);
    }

    public Integer getSessionTimeoutMs() {
        return configuration.getSessionTimeoutMs();
    }

    public Integer getBufferMemorySize() {
        return configuration.getBufferMemorySize();
    }

    public String getKeySerializerClass() {
        return configuration.getKeySerializerClass();
    }

    public void setSslProvider(String sslProvider) {
        configuration.setSslProvider(sslProvider);
    }

    public void setFetchMinBytes(Integer fetchMinBytes) {
        configuration.setFetchMinBytes(fetchMinBytes);
    }

    public Integer getAutoCommitIntervalMs() {
        return configuration.getAutoCommitIntervalMs();
    }

    public void setKeySerializerClass(String keySerializerClass) {
        configuration.setKeySerializerClass(keySerializerClass);
    }

    public Integer getConnectionMaxIdleMs() {
        return configuration.getConnectionMaxIdleMs();
    }

    public Integer getReceiveBufferBytes() {
        return configuration.getReceiveBufferBytes();
    }

    public void setBrokers(String brokers) {
        configuration.setBrokers(brokers);
    }

    public String getValueDeserializer() {
        return configuration.getValueDeserializer();
    }

    public String getPartitioner() {
        return configuration.getPartitioner();
    }

    public String getSslTruststoreLocation() {
        return configuration.getSslTruststoreLocation();
    }

    public void setBarrierAwaitTimeoutMs(int barrierAwaitTimeoutMs) {
        configuration.setBarrierAwaitTimeoutMs(barrierAwaitTimeoutMs);
    }

    public String getSslProvider() {
        return configuration.getSslProvider();
    }

    public void setMetricReporters(String metricReporters) {
        configuration.setMetricReporters(metricReporters);
    }

    public void setSslTruststorePassword(String sslTruststorePassword) {
        configuration.setSslTruststorePassword(sslTruststorePassword);
    }

    public void setMaxInFlightRequest(Integer maxInFlightRequest) {
        configuration.setMaxInFlightRequest(maxInFlightRequest);
    }

    public String getTopic() {
        return configuration.getTopic();
    }

    public int getBarrierAwaitTimeoutMs() {
        return configuration.getBarrierAwaitTimeoutMs();
    }

    public Integer getFetchMinBytes() {
        return configuration.getFetchMinBytes();
    }

    public Integer getHeartbeatIntervalMs() {
        return configuration.getHeartbeatIntervalMs();
    }

    public void setKeyDeserializer(String keyDeserializer) {
        configuration.setKeyDeserializer(keyDeserializer);
    }

    public Integer getMaxRequestSize() {
        return configuration.getMaxRequestSize();
    }

    public void setMetadataMaxAgeMs(Integer metadataMaxAgeMs) {
        configuration.setMetadataMaxAgeMs(metadataMaxAgeMs);
    }

    public String getSslKeystoreType() {
        return configuration.getSslKeystoreType();
    }

    public void setKerberosRenewWindowFactor(Double kerberosRenewWindowFactor) {
        configuration.setKerberosRenewWindowFactor(kerberosRenewWindowFactor);
    }

    public Integer getKerberosBeforeReloginMinTime() {
        return configuration.getKerberosBeforeReloginMinTime();
    }

    public String getSslEnabledProtocols() {
        return configuration.getSslEnabledProtocols();
    }

    public Integer getMaxInFlightRequest() {
        return configuration.getMaxInFlightRequest();
    }

    public Integer getProducerBatchSize() {
        return configuration.getProducerBatchSize();
    }

    public void setSslKeystorePassword(String sslKeystorePassword) {
        configuration.setSslKeystorePassword(sslKeystorePassword);
    }

    public void setCheckCrcs(Boolean checkCrcs) {
        configuration.setCheckCrcs(checkCrcs);
    }

    public int getConsumerStreams() {
        return configuration.getConsumerStreams();
    }

    public void setConsumersCount(int consumersCount) {
        configuration.setConsumersCount(consumersCount);
    }

    public int getBatchSize() {
        return configuration.getBatchSize();
    }

    public void setAutoCommitIntervalMs(Integer autoCommitIntervalMs) {
        configuration.setAutoCommitIntervalMs(autoCommitIntervalMs);
    }

    public void setSslTruststoreType(String sslTruststoreType) {
        configuration.setSslTruststoreType(sslTruststoreType);
    }

    public Integer getConsumerRequestTimeoutMs() {
        return configuration.getConsumerRequestTimeoutMs();
    }

    public String getSslKeystorePassword() {
        return configuration.getSslKeystorePassword();
    }

    public void setSslKeyPassword(String sslKeyPassword) {
        configuration.setSslKeyPassword(sslKeyPassword);
    }

    public Integer getRequestRequiredAcks() {
        return configuration.getRequestRequiredAcks();
    }

    public Double getKerberosRenewWindowFactor() {
        return configuration.getKerberosRenewWindowFactor();
    }

    public void setKerberosInitCmd(String kerberosInitCmd) {
        configuration.setKerberosInitCmd(kerberosInitCmd);
    }

    public Integer getRetryBackoffMs() {
        return configuration.getRetryBackoffMs();
    }

    public void setSslTrustmanagerAlgorithm(String sslTrustmanagerAlgorithm) {
        configuration.setSslTrustmanagerAlgorithm(sslTrustmanagerAlgorithm);
    }

    public void setConsumerRequestTimeoutMs(Integer consumerRequestTimeoutMs) {
        configuration.setConsumerRequestTimeoutMs(consumerRequestTimeoutMs);
    }

    public void setReconnectBackoffMs(Integer reconnectBackoffMs) {
        configuration.setReconnectBackoffMs(reconnectBackoffMs);
    }

    public void setKerberosRenewJitter(Double kerberosRenewJitter) {
        configuration.setKerberosRenewJitter(kerberosRenewJitter);
    }

    public String getSslKeystoreLocation() {
        return configuration.getSslKeystoreLocation();
    }

    public Integer getNoOfMetricsSample() {
        return configuration.getNoOfMetricsSample();
    }

    public String getSslKeymanagerAlgorithm() {
        return configuration.getSslKeymanagerAlgorithm();
    }

    public void setConsumerId(String consumerId) {
        configuration.setConsumerId(consumerId);
    }

    public String getClientId() {
        return configuration.getClientId();
    }

    public void setFetchWaitMaxMs(Integer fetchWaitMaxMs) {
        configuration.setFetchWaitMaxMs(fetchWaitMaxMs);
    }

    public String getSslCipherSuites() {
        return configuration.getSslCipherSuites();
    }

    public void setRequestRequiredAcks(Integer requestRequiredAcks) {
        configuration.setRequestRequiredAcks(requestRequiredAcks);
    }

    public void setConnectionMaxIdleMs(Integer connectionMaxIdleMs) {
        configuration.setConnectionMaxIdleMs(connectionMaxIdleMs);
    }

    public String getSslTrustmanagerAlgorithm() {
        return configuration.getSslTrustmanagerAlgorithm();
    }

    public String getSslTruststorePassword() {
        return configuration.getSslTruststorePassword();
    }

    public void setConsumerStreams(int consumerStreams) {
        configuration.setConsumerStreams(consumerStreams);
    }

    public String getSslTruststoreType() {
        return configuration.getSslTruststoreType();
    }

    public String getSecurityProtocol() {
        return configuration.getSecurityProtocol();
    }

    public void setBufferMemorySize(Integer bufferMemorySize) {
        configuration.setBufferMemorySize(bufferMemorySize);
    }

    public void setSaslKerberosServiceName(String saslKerberosServiceName) {
        configuration.setSaslKerberosServiceName(saslKerberosServiceName);
    }

    public void setCompressionCodec(String compressionCodec) {
        configuration.setCompressionCodec(compressionCodec);
    }

    public void setKerberosBeforeReloginMinTime(Integer kerberosBeforeReloginMinTime) {
        configuration.setKerberosBeforeReloginMinTime(kerberosBeforeReloginMinTime);
    }

    public Integer getMetadataMaxAgeMs() {
        return configuration.getMetadataMaxAgeMs();
    }

    public String getSerializerClass() {
        return configuration.getSerializerClass();
    }

    public void setSslKeymanagerAlgorithm(String sslKeymanagerAlgorithm) {
        configuration.setSslKeymanagerAlgorithm(sslKeymanagerAlgorithm);
    }

    public void setMaxRequestSize(Integer maxRequestSize) {
        configuration.setMaxRequestSize(maxRequestSize);
    }

    public Double getKerberosRenewJitter() {
        return configuration.getKerberosRenewJitter();
    }

    public String getPartitionAssignor() {
        return configuration.getPartitionAssignor();
    }

    public void setSecurityProtocol(String securityProtocol) {
        configuration.setSecurityProtocol(securityProtocol);
    }

    public void setQueueBufferingMaxMessages(Integer queueBufferingMaxMessages) {
        configuration.setQueueBufferingMaxMessages(queueBufferingMaxMessages);
    }

    public String getSaslKerberosServiceName() {
        return configuration.getSaslKerberosServiceName();
    }

    public void setBatchSize(int batchSize) {
        configuration.setBatchSize(batchSize);
    }

    public Integer getLingerMs() {
        return configuration.getLingerMs();
    }

    public Integer getRetries() {
        return configuration.getRetries();
    }

    public Integer getMaxPartitionFetchBytes() {
        return configuration.getMaxPartitionFetchBytes();
    }

    public String getSslEndpointAlgorithm() {
        return configuration.getSslEndpointAlgorithm();
    }

    public Integer getReconnectBackoffMs() {
        return configuration.getReconnectBackoffMs();
    }

    public void setLingerMs(Integer lingerMs) {
        configuration.setLingerMs(lingerMs);
    }

    public void setPartitionAssignor(String partitionAssignor) {
        configuration.setPartitionAssignor(partitionAssignor);
    }

    public Integer getRequestTimeoutMs() {
        return configuration.getRequestTimeoutMs();
    }

    public Properties createConsumerProperties() {
        return configuration.createConsumerProperties();
    }

    public void setTopic(String topic) {
        configuration.setTopic(topic);
    }

    public Integer getFetchWaitMaxMs() {
        return configuration.getFetchWaitMaxMs();
    }

    public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
        configuration.setSessionTimeoutMs(sessionTimeoutMs);
    }

    public void setSslEnabledProtocols(String sslEnabledProtocols) {
        configuration.setSslEnabledProtocols(sslEnabledProtocols);
    }

    public void setHeartbeatIntervalMs(Integer heartbeatIntervalMs) {
        configuration.setHeartbeatIntervalMs(heartbeatIntervalMs);
    }

    public void setMaxBlockMs(Integer maxBlockMs) {
        configuration.setMaxBlockMs(maxBlockMs);
    }

    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        configuration.setSslKeystoreLocation(sslKeystoreLocation);
    }

    public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
        configuration.setMaxPartitionFetchBytes(maxPartitionFetchBytes);
    }

    public void setPartitioner(String partitioner) {
        configuration.setPartitioner(partitioner);
    }

    public String getBrokers() {
        return configuration.getBrokers();
    }

    public Integer getMetricsSampleWindowMs() {
        return configuration.getMetricsSampleWindowMs();
    }

    public Integer getSendBufferBytes() {
        return configuration.getSendBufferBytes();
    }

    public String getSslProtocol() {
        return configuration.getSslProtocol();
    }

    public boolean isSeekToBeginning() {
        return configuration.isSeekToBeginning();
    }

    public void setSeekToBeginning(boolean seekToBeginning) {
        configuration.setSeekToBeginning(seekToBeginning);
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, then KafkaProducer will ignore the KafkaConstants.TOPIC header setting of the inbound message.
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

}
