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

package org.apache.camel.component.google.pubsublite;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings;
import com.google.cloud.pubsublite.SubscriptionPath;
import com.google.cloud.pubsublite.TopicPath;
import com.google.cloud.pubsublite.cloudpubsub.FlowControlSettings;
import com.google.cloud.pubsublite.cloudpubsub.Publisher;
import com.google.cloud.pubsublite.cloudpubsub.PublisherSettings;
import com.google.cloud.pubsublite.cloudpubsub.Subscriber;
import com.google.cloud.pubsublite.cloudpubsub.SubscriberSettings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link GooglePubsubLiteEndpoint}.
 */
@Component("google-pubsub-lite")
public class GooglePubsubLiteComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(GooglePubsubLiteComponent.class);

    @Metadata(
            label = "security",
            description =
                    "The Service account key that can be used as credentials for the PubSub Lite publisher/subscriber. It can be loaded by default from "
                            + " classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.")
    private String serviceAccountKey;

    @Metadata(
            label = "producer,advanced",
            defaultValue = "100",
            description =
                    "Maximum number of producers to cache. This could be increased if you have producers for lots of different topics.")
    private int publisherCacheSize = 100;

    @Metadata(
            label = "producer,advanced",
            defaultValue = "180000",
            description = "How many milliseconds should each producer stay alive in the cache.")
    private int publisherCacheTimeout = 180000;

    @Metadata(
            label = "consumer,advanced",
            defaultValue = "10485760",
            description = "The number of quota bytes that may be outstanding to the client. "
                    + "Must be greater than the allowed size of the largest message (1 MiB).")
    private long consumerBytesOutstanding = 10 * 1024 * 1024L;

    @Metadata(
            label = "consumer,advanced",
            defaultValue = "1000",
            description = "The number of messages that may be outstanding to the client. Must be >0.")
    private long consumerMessagesOutstanding = 1000;

    @Metadata(
            label = "producer,advanced",
            defaultValue = "60000",
            description = "How many milliseconds should a producer be allowed to terminate.")
    private int publisherTerminationTimeout = 60000;

    private RemovalListener<String, Publisher> removalListener = removal -> {
        Publisher publisher = removal.getValue();
        if (ObjectHelper.isNotEmpty(publisher)) {
            return;
        }
        publisher.stopAsync();
        try {
            publisher.awaitTerminated(publisherTerminationTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    };

    private Cache<String, Publisher> cachedPublishers = CacheBuilder.newBuilder()
            .expireAfterWrite(publisherCacheTimeout, TimeUnit.MILLISECONDS)
            .maximumSize(publisherCacheSize)
            .removalListener(removalListener)
            .build();

    public GooglePubsubLiteComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        String[] parts = remaining.split(":");

        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Google PubSub Lite Endpoint format \"projectId:location:destinationName[:subscriptionName]\"");
        }

        GooglePubsubLiteEndpoint pubsubEndpoint = new GooglePubsubLiteEndpoint(uri, this);
        LOG.debug("Google Cloud ProjectId {}", parts[0]);
        pubsubEndpoint.setProjectId(parts[0]);
        LOG.debug("Google Cloud LocationId {}", parts[1]);
        pubsubEndpoint.setLocation(parts[1]);
        LOG.debug("Google Cloud DestinationName {}", parts[2]);
        pubsubEndpoint.setDestinationName(parts[2]);
        LOG.debug("Google Cloud ServiceAccountKey {}", serviceAccountKey);
        pubsubEndpoint.setServiceAccountKey(serviceAccountKey);

        setProperties(pubsubEndpoint, parameters);

        return pubsubEndpoint;
    }

    @Override
    protected void doShutdown() throws Exception {
        cachedPublishers.cleanUp();
        cachedPublishers.invalidateAll();
        super.doShutdown();
    }

    public Publisher getPublisher(String topicName, GooglePubsubLiteEndpoint googlePubsubEndpoint)
            throws ExecutionException {
        return cachedPublishers.get(topicName, () -> buildPublisher(googlePubsubEndpoint));
    }

    private Publisher buildPublisher(GooglePubsubLiteEndpoint googlePubsubLiteEndpoint) throws IOException {

        TopicPath topicPath = TopicPath.parse(String.format(
                "projects/%s/locations/%s/topics/%s",
                googlePubsubLiteEndpoint.getProjectId(),
                googlePubsubLiteEndpoint.getLocation(),
                googlePubsubLiteEndpoint.getDestinationName()));

        PublisherSettings publisherSettings = PublisherSettings.newBuilder()
                .setTopicPath(topicPath)
                .setCredentialsProvider(getCredentialsProvider(googlePubsubLiteEndpoint))
                .build();
        Publisher publisher = Publisher.create(publisherSettings);
        publisher.startAsync().awaitRunning();
        return publisher;
    }

    public Subscriber getSubscriber(MessageReceiver messageReceiver, GooglePubsubLiteEndpoint googlePubsubLiteEndpoint)
            throws IOException {

        SubscriptionPath subscriptionPath = SubscriptionPath.parse(String.format(
                "projects/%s/locations/%s/subscriptions/%s",
                googlePubsubLiteEndpoint.getProjectId(),
                googlePubsubLiteEndpoint.getLocation(),
                googlePubsubLiteEndpoint.getDestinationName()));

        LOG.debug("ConsumerBytesOutstanding {}", consumerBytesOutstanding);
        LOG.debug("ConsumerMessagesOutstanding {}", consumerMessagesOutstanding);

        // The message stream is paused based on the maximum size or number of messages that the
        // subscriber has already received, whichever condition is met first.
        FlowControlSettings flowControlSettings = FlowControlSettings.builder()
                .setBytesOutstanding(consumerBytesOutstanding)
                .setMessagesOutstanding(consumerMessagesOutstanding)
                .build();

        SubscriberSettings subscriberSettings = SubscriberSettings.newBuilder()
                .setSubscriptionPath(subscriptionPath)
                .setReceiver(messageReceiver)
                // Flow control settings are set at the partition level.
                .setPerPartitionFlowControlSettings(flowControlSettings)
                .setCredentialsProvider(getCredentialsProvider(googlePubsubLiteEndpoint))
                .build();

        return Subscriber.create(subscriberSettings);
    }

    private CredentialsProvider getCredentialsProvider(GooglePubsubLiteEndpoint endpoint) throws IOException {
        return FixedCredentialsProvider.create(
                ObjectHelper.isEmpty(endpoint.getServiceAccountKey())
                        ? GoogleCredentials.getApplicationDefault()
                        : ServiceAccountCredentials.fromStream(ResourceHelper.resolveMandatoryResourceAsInputStream(
                                        getCamelContext(), endpoint.getServiceAccountKey()))
                                .createScoped(PublisherStubSettings.getDefaultServiceScopes()));
    }

    public int getPublisherCacheSize() {
        return publisherCacheSize;
    }

    public void setPublisherCacheSize(int publisherCacheSize) {
        this.publisherCacheSize = publisherCacheSize;
    }

    public int getPublisherCacheTimeout() {
        return publisherCacheTimeout;
    }

    public void setPublisherCacheTimeout(int publisherCacheTimeout) {
        this.publisherCacheTimeout = publisherCacheTimeout;
    }

    public int getPublisherTerminationTimeout() {
        return publisherTerminationTimeout;
    }

    public void setPublisherTerminationTimeout(int publisherTerminationTimeout) {
        this.publisherTerminationTimeout = publisherTerminationTimeout;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public long getConsumerBytesOutstanding() {
        return consumerBytesOutstanding;
    }

    public void setConsumerBytesOutstanding(long consumerBytesOutstanding) {
        this.consumerBytesOutstanding = consumerBytesOutstanding;
    }

    public long getConsumerMessagesOutstanding() {
        return consumerMessagesOutstanding;
    }

    public void setConsumerMessagesOutstanding(long consumerMessagesOutstanding) {
        this.consumerMessagesOutstanding = consumerMessagesOutstanding;
    }
}
