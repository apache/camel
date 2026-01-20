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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.consumergroup.ConsumerGroupDetails;
import org.apache.iggy.identifier.ConsumerId;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.stream.StreamDetails;
import org.apache.iggy.topic.TopicDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive message to Apache Iggy streaming platform.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "iggy", title = "Iggy", syntax = "iggy:topicName",
             category = { Category.MESSAGING }, headersClass = IggyConstants.class)
public class IggyEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(IggyEndpoint.class);

    @UriParam
    private IggyConfiguration configuration;
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
                        TopicDetails topicDetails = client.topics().createTopic(StreamId.of(stream),
                                iggyConfiguration.getPartitionsCount(),
                                iggyConfiguration.getCompressionAlgorithm(),
                                BigInteger.valueOf(iggyConfiguration.getMessageExpiry()),
                                BigInteger.valueOf(iggyConfiguration.getMaxTopicSize()),
                                Optional.ofNullable(iggyConfiguration.getReplicationFactor()),
                                topic);

                        LOG.debug("Topic created or retrieved with details: {}", topicDetails.toString());

                        return topicDetails;
                    });
        }
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
