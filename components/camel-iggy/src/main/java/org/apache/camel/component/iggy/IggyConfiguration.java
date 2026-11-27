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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.topic.CompressionAlgorithm;

@UriParams
public class IggyConfiguration implements Cloneable {

    @UriParam(defaultValue = "localhost", description = "The hostname or IP address of the Iggy server.")
    private String host = "localhost";
    @UriParam(defaultValue = "8090",
              description = "The port of the Iggy server. The default 8090 is the default port of the TCP transport of the"
                            + " server, so set the HTTP port of the server when clientTransport is HTTP.")
    private int port = 8090;
    @UriParam(security = "secret", label = "security",
              description = "The username that the client logs in with to the Iggy server. Required, together with the"
                            + " password option: the client fails to connect without them.")
    private String username;
    @UriParam(security = "secret", label = "security",
              description = "The password that the client logs in with to the Iggy server. Required, together with the"
                            + " username option: the client fails to connect without them.")
    private String password;
    @UriParam(defaultValue = "true",
              description = "Whether to create the stream if it does not exist. The producer and the consumer check it when"
                            + " they start, and the producer again for each message that has an override header.")
    private boolean autoCreateStream = true;
    @UriParam(defaultValue = "true",
              description = "Whether to create the topic if it does not exist, with the partitionsCount,"
                            + " compressionAlgorithm, messageExpiry, maxTopicSize and iggyHeaderOptions options. The producer"
                            + " and the consumer check it when they start, and the producer again for each message that has"
                            + " an override header.")
    private boolean autoCreateTopic = true;
    @UriParam(description = "The ID of a stream that autoCreateStream looks up for the stream of the endpoint: if no stream"
                            + " has this ID, a stream named streamName is created. The stream of a CamelIggyStreamOverride"
                            + " header is looked up and created by its name. The producer and the consumer always address"
                            + " the stream by its name.")
    private Long streamId;
    @UriParam(description = "The name of the stream that holds the topic. Required. The CamelIggyStreamOverride header"
                            + " replaces it for the producer.",
              endpointIdentity = true)
    private String streamName;
    @UriParam(defaultValue = "1", description = "The number of partitions of a topic that autoCreateTopic creates.")
    private Long partitionsCount = 1L;
    @UriParam(defaultValue = "None", enums = "None,Gzip",
              description = "The compression algorithm of a topic that autoCreateTopic creates.")
    private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.None;
    @UriParam(defaultValue = "0",
              description = "The time in seconds after which the server deletes the messages of a topic that autoCreateTopic"
                            + " creates. With 0 no expiry is set on the topic.")
    private Long messageExpiry = 0L;
    @UriParam(defaultValue = "0",
              description = "The maximum size in bytes of a topic that autoCreateTopic creates. With 0 no maximum size is"
                            + " set on the topic.")
    private Long maxTopicSize = 0L;
    @UriParam(description = "Additional options of a topic that autoCreateTopic creates, as key-value pairs that are sent to"
                            + " the server as strings, for example durability.")
    private Map<String, String> iggyHeaderOptions = new HashMap<>();
    @UriParam(label = "producer", defaultValue = "balanced",
              description = "How the producer chooses the partition of the messages it sends, as an"
                            + " org.apache.iggy.message.Partitioning or as text: balanced() (the text balanced) spreads them"
                            + " over the partitions, partitionId(Long) (for example the text partitionId:2) sends to one"
                            + " partition and messagesKey(String) (for example the text messagesKey:order-1) to the partition"
                            + " of a key.")
    private Partitioning partitioning = Partitioning.balanced();
    @UriParam(label = "consumer",
              description = "The name of the consumer group that the consumer joins, and creates if it does not exist."
                            + " Required for the consumer.")
    private String consumerGroupName;
    @UriParam(label = "consumer", defaultValue = "10",
              description = "The maximum number of messages that the consumer polls in one request.")
    private Long pollBatchSize = 10L;
    @UriParam(label = "consumer",
              description = "The ID of the partition that the consumer polls. If not set, the server chooses the partition"
                            + " for the member of the consumer group.")
    private Long partitionId;
    @UriParam(label = "consumer", defaultValue = "1",
              description = "The number of threads of the thread pool of the consumer. The consumer runs one polling task, so"
                            + " one thread polls the topic.")
    @Metadata(deprecationNote = "The consumer polls the topic with one task, so a value above 1 only adds idle threads")
    @Deprecated(since = "4.24.0")
    private int consumersCount = 1;
    @UriParam(label = "consumer", defaultValue = "30000",
              description = "The time in milliseconds that the consumer waits for its polling thread to finish when it stops,"
                            + " before it interrupts the thread.")
    private int shutdownTimeout = 30000;
    @UriParam(label = "consumer", defaultValue = "next", enums = "next,first,last",
              description = "Where the consumer polls from when autoCommit is true: next polls the messages after the offset"
                            + " stored for the consumer group, first the first messages of the partition, and last the last"
                            + " messages of the partition.")
    private String pollingStrategy = "next";
    @UriParam(defaultValue = "TCP", enums = "TCP,HTTP",
              description = "The transport that the client uses to connect to the Iggy server.")
    private String clientTransport = "TCP"; // There may be QUIC in the future
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the server stores the offset of the consumer group when it returns the polled messages,"
                            + " before the route processes them. If false, the consumer polls from startingOffset and keeps"
                            + " the offset in memory only, so it starts again from startingOffset after a restart.")
    private boolean autoCommit = true;
    @UriParam(label = "consumer", defaultValue = "0",
              description = "The offset of the first message that the consumer polls when autoCommit is false. 0 is the first"
                            + " message of the partition.")
    private Long startingOffset = 0L;
    @UriParam(label = "security", defaultValue = "false",
              description = "Whether the client connects to the Iggy server over TLS.")
    private boolean tlsEnabled;
    @UriParam(label = "security",
              description = "The path to a certificate file in PEM format that the client trusts for the TLS connection to"
                            + " the Iggy server. If not set, the client uses the default trust store of the JVM.")
    private String tlsCertificatePath;
    @UriParam(label = "security",
              description = "SSL configuration using an org.apache.camel.support.jsse.SSLContextParameters instance."
                            + " This takes precedence over tlsEnabled and tlsCertificatePath when configured.")
    private SSLContextParameters sslContextParameters;

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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

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

    @Deprecated(since = "4.24.0")
    public int getConsumersCount() {
        return consumersCount;
    }

    /**
     * @deprecated the consumer polls the topic with one task, so a value above 1 only adds idle threads
     */
    @Deprecated(since = "4.24.0")
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

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public String getTlsCertificatePath() {
        return tlsCertificatePath;
    }

    public void setTlsCertificatePath(String tlsCertificatePath) {
        this.tlsCertificatePath = tlsCertificatePath;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * SSL configuration using an org.apache.camel.support.jsse.SSLContextParameters instance. This takes precedence
     * over tlsEnabled and tlsCertificatePath when configured.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Map<String, String> getIggyHeaderOptions() {
        return iggyHeaderOptions;
    }

    public void setIggyHeaderOptions(Map<String, String> iggyHeaderOptions) {
        this.iggyHeaderOptions = iggyHeaderOptions;
    }
}
