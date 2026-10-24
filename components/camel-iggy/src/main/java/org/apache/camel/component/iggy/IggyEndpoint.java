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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.consumergroup.ConsumerGroupDetails;
import org.apache.iggy.identifier.ConsumerId;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.HeaderValue;
import org.apache.iggy.stream.StreamDetails;
import org.apache.iggy.topic.TopicDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive message to Apache Iggy streaming platform.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "iggy", title = "Iggy", syntax = "iggy:topicName",
             category = { Category.MESSAGING }, headersClass = IggyConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   description = "The payload of the Iggy message, decoded as text with the default charset"
                                                 + " of the JVM: a binary payload does not survive"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is converted to text and sent as one message, so bytes are"
                                                + " decoded as text (a binary payload is corrupted) and a Map or a POJO is"
                                                + " sent as its toString(): marshal it first. A List of String or of Iggy"
                                                + " Message is sent as one message per element, and an Iggy Message as it"
                                                + " is. No body or an empty List fails"),
           producerOut = @BodyType(Carrier.UNCHANGED))
public class IggyEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final BigInteger MICROS_PER_SECOND = BigInteger.valueOf(1_000_000L);
    private static final BigInteger U64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private static final Logger LOG = LoggerFactory.getLogger(IggyEndpoint.class);

    @UriParam
    private IggyConfiguration configuration;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriPath(description = "Name of the topic")
    @Metadata(required = true)
    private String topicName;

    public IggyEndpoint(String endpointUri, IggyComponent component, IggyConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IggyProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        IggyConsumer consumer = new IggyConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public void initializeTopic(IggyBaseClient client, String topicName, String streamName) throws Exception {
        IggyConfiguration iggyConfiguration = getConfiguration();
        Objects.requireNonNull(iggyConfiguration.getStreamName(), "The stream name is required");
        String topic = topicName != null ? topicName : getTopicName();
        String stream = streamName != null ? streamName : iggyConfiguration.getStreamName();

        if (iggyConfiguration.isAutoCreateStream()) {
            if (iggyConfiguration.getStreamId() != null) {
                client.streams().getStream(StreamId.of(iggyConfiguration.getStreamId())).orElseGet(() -> {
                    LOG.debug("Creating stream with id {} and name {}", iggyConfiguration.getStreamId(),
                            iggyConfiguration.getStreamName());
                    StreamDetails streamDetails = client.streams().createStream(iggyConfiguration.getStreamName());

                    LOG.debug("Stream created with details: {}", streamDetails.toString());

                    return streamDetails;
                });
            } else {
                client.streams().getStream(StreamId.of(stream)).orElseGet(() -> {
                    LOG.debug("Creating stream with name {}", stream);
                    StreamDetails streamDetails = client.streams().createStream(stream);
                    LOG.debug("Stream created with details: {}", streamDetails.toString());

                    return streamDetails;
                });
            }

        }

        if (iggyConfiguration.isAutoCreateTopic()) {
            client.topics()
                    .getTopic(StreamId.of(stream), TopicId.of(topic))
                    .orElseGet(() -> {
                        LOG.debug("Creating topic with name {}", topic);
                        Map<String, HeaderValue> options = new HashMap<>();
                        for (Map.Entry<String, String> optionEntry : iggyConfiguration.getIggyHeaderOptions().entrySet()) {
                            options.put(optionEntry.getKey(), HeaderValue.fromString(optionEntry.getValue()));
                        }

                        TopicDetails topicDetails = client.topics().createTopic(StreamId.of(stream),
                                iggyConfiguration.getPartitionsCount(),
                                iggyConfiguration.getCompressionAlgorithm(),
                                toIggyExpiry(iggyConfiguration.getMessageExpiry()),
                                BigInteger.valueOf(iggyConfiguration.getMaxTopicSize()),
                                topic,
                                options);

                        LOG.debug("Topic created or retrieved with details: {}", topicDetails.toString());

                        return topicDetails;
                    });
        }
    }

    /**
     * Converts the messageExpiry option, in seconds, to the message expiry of an Iggy topic, which is in microseconds.
     * 0 is passed as 0 (no expiry is set on the topic) and a negative value is passed unchanged, as before; a value too
     * large for an unsigned 64-bit number of microseconds becomes u64::MAX, which Iggy reads as never expire.
     */
    static BigInteger toIggyExpiry(Long seconds) {
        if (seconds == null) {
            return BigInteger.ZERO;
        }
        if (seconds <= 0) {
            return BigInteger.valueOf(seconds);
        }
        BigInteger micros = BigInteger.valueOf(seconds).multiply(MICROS_PER_SECOND);
        return micros.compareTo(U64_MAX) >= 0 ? U64_MAX : micros;
    }

    public void initializeTopic(IggyBaseClient client) throws Exception {
        initializeTopic(client, null, null);
    }

    public void initializeConsumerGroup(IggyBaseClient client) {
        Objects.requireNonNull(getConfiguration().getConsumerGroupName(), "Consumer group name is required");

        client.consumerGroups()
                .getConsumerGroup(
                        StreamId.of(getConfiguration().getStreamName()),
                        TopicId.of(getTopicName()),
                        ConsumerId.of(getConfiguration().getConsumerGroupName()))
                .orElseGet(() -> {
                    LOG.debug("Creating consumer group with name {}", configuration.getConsumerGroupName());

                    ConsumerGroupDetails consumerGroupDetails = client.consumerGroups().createConsumerGroup(
                            StreamId.of(getConfiguration().getStreamName()),
                            TopicId.of(getTopicName()),
                            getConfiguration().getConsumerGroupName());

                    LOG.debug("Created consumer group {}", consumerGroupDetails);

                    return consumerGroupDetails;
                });

        joinConsumerGroup(client);
    }

    /**
     * Makes the client a member of the consumer group. The membership belongs to the connection of the client, so each
     * client the consumer polls with must join.
     */
    public void joinConsumerGroup(IggyBaseClient client) {
        client.consumerGroups().joinConsumerGroup(
                StreamId.of(getConfiguration().getStreamName()),
                TopicId.of(getTopicName()),
                ConsumerId.of(getConfiguration().getConsumerGroupName()));

        LOG.debug("The client joined the consumer group on the stream {}, topic {} and consumer {}",
                getConfiguration().getStreamName(),
                getTopicName(),
                getConfiguration().getConsumerGroupName());
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                "IggyConsumer[" + getTopicName() + "]", configuration.getConsumersCount());
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new DefaultHeaderFilterStrategy();
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public IggyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IggyConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
}
