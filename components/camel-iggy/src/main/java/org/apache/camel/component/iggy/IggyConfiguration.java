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
package org.apache.camel.component.iggy;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.topic.CompressionAlgorithm;

@UriParams
public class IggyConfiguration implements Cloneable {

    @UriParam(defaultValue = "localhost", description = "Iggy server hostname or IP address")
    private String host = "localhost";
    @UriParam(defaultValue = "8090", description = "Iggy server port number")
    private int port = 8090;
    @UriParam(secret = true, label = "security", description = "Iggy username")
    private String username;
    @UriParam(secret = true, description = "Iggy password")
    private String password;
    @UriParam(defaultValue = "true",
              description = "Whether to automatically create stream if it does not exist")
    private boolean autoCreateStream = true;
    @UriParam(defaultValue = "true",
              description = "Whether to automatically create topic if it does not exist")
    private boolean autoCreateTopic = true;
    @UriParam(description = "Stream identifier")
    private Long streamId;
    @UriParam(description = "Stream name")
    private String streamName;
    @UriParam(defaultValue = "1", description = "Number of partitions for the topic")
    private Long partitionsCount = 1L;
    @UriParam(defaultValue = "None", enums = "None,Gzip",
              description = "Compression algorithm for message payload")
    private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.None;
    @UriParam(defaultValue = "0", description = "Message expiry time in seconds (0 means no expiry)")
    private Long messageExpiry = 0L;
    @UriParam(defaultValue = "0", description = "Maximum topic size in bytes (0 means unlimited)")
    private Long maxTopicSize = 0L;
    @UriParam(description = "Replication factor for the topic")
    private Short replicationFactor;
    @UriParam(label = "producer", defaultValue = "balanced", description = "Partitioning strategy for message distribution")
    private Partitioning partitioning = Partitioning.balanced();
    @UriParam(label = "consumer", description = "The name of the consumer group")
    private String consumerGroupName;
    @UriParam(label = "consumer", description = "The consumer poll batch size", defaultValue = "10")
    private Long pollBatchSize = 10L;
    @UriParam(label = "consumer", description = "The consumer partition id")
    private Long partitionId;
    @UriParam(label = "consumer", defaultValue = "1", description = "Camel Iggy consumers count")
    private int consumersCount = 1;
    @UriParam(label = "common,consumer", defaultValue = "30000", description = "Camel Iggy shutdown timeout")
    private int shutdownTimeout = 30000;
    @UriParam(label = "consumer", defaultValue = "next", description = "Polling strategy", enums = "next,first,last")
    private String pollingStrategy = "next";
    @UriParam(defaultValue = "TCP", description = "Polling strategy", enums = "TCP,HTTP")
    private String clientTransport = "TCP"; // There may be QUIC in the future
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Controls message acknowledgment behavior. " +
                            "When true, messages are automatically marked as processed after consumption. " +
                            "When false, enables manual offset management and allows setting a custom starting offset position")
    private boolean autoCommit = true;
    @UriParam(label = "consumer", defaultValue = "0",
              description = "Defines the initial message offset position when autoCommit is disabled. " +
                            "Use 0 to start from the beginning of the stream, or specify a custom offset to resume from a particular point")
    private Long startingOffset;

    public IggyConfiguration copy() {
        try {
            return (IggyConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getHost() {
        return host;
    }

    /**
     * The hostname of the Iggy server.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port of the Iggy server.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAutoCreateStream() {
        return autoCreateStream;
    }

    public void setAutoCreateStream(boolean autoCreateStream) {
        this.autoCreateStream = autoCreateStream;
    }

    public boolean isAutoCreateTopic() {
        return autoCreateTopic;
    }

    public void setAutoCreateTopic(boolean autoCreateTopic) {
        this.autoCreateTopic = autoCreateTopic;
    }

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(Long streamId) {
        this.streamId = streamId;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public Long getPartitionsCount() {
        return partitionsCount;
    }

    public void setPartitionsCount(Long partitionsCount) {
        this.partitionsCount = partitionsCount;
    }

    public CompressionAlgorithm getCompressionAlgorithm() {
        return compressionAlgorithm;
    }

    public void setCompressionAlgorithm(CompressionAlgorithm compressionAlgorithm) {
        this.compressionAlgorithm = compressionAlgorithm;
    }

    public Long getMessageExpiry() {
        return messageExpiry;
    }

    public void setMessageExpiry(Long messageExpiry) {
        this.messageExpiry = messageExpiry;
    }

    public Long getMaxTopicSize() {
        return maxTopicSize;
    }

    public void setMaxTopicSize(Long maxTopicSize) {
        this.maxTopicSize = maxTopicSize;
    }

    public Short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(Short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public Partitioning getPartitioning() {
        return partitioning;
    }

    public void setPartitioning(Partitioning partitioning) {
        this.partitioning = partitioning;
    }

    public String getConsumerGroupName() {
        return consumerGroupName;
    }

    public void setConsumerGroupName(String consumerGroupName) {
        this.consumerGroupName = consumerGroupName;
    }

    public Long getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(Long pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }

    public Long getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(Long partitionId) {
        this.partitionId = partitionId;
    }

    public int getConsumersCount() {
        return consumersCount;
    }

    public void setConsumersCount(int consumersCount) {
        this.consumersCount = consumersCount;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public String getPollingStrategy() {
        return pollingStrategy;
    }

    public void setPollingStrategy(String pollingStrategy) {
        this.pollingStrategy = pollingStrategy;
    }

    public String getClientTransport() {
        return clientTransport;
    }

    public void setClientTransport(String clientTransport) {
        this.clientTransport = clientTransport;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public Long getStartingOffset() {
        return startingOffset;
    }

    public void setStartingOffset(Long startingOffset) {
        this.startingOffset = startingOffset;
    }
}
