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
package org.apache.camel.component.google.pubsub;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link GooglePubsubEndpoint}.
 */
@Component("google-pubsub")
public class GooglePubsubComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(GooglePubsubComponent.class);

    @Metadata(
              label = "common",
              description = "Endpoint to use with local Pub/Sub emulator.")
    private String endpoint;

    @Metadata(label = "common",
              description = "The Service account key that can be used as credentials for the PubSub publisher/subscriber. It can be loaded by default from "
                            + " classpath, but you can prefix with classpath:, file:, or http: to load the resource from different systems.",
              required = true)
    private String serviceAccountKey;

    @Metadata(
              label = "producer",
              description = "Maximum number of producers to cache. This could be increased if you have producers for lots of different topics.")
    private int publisherCacheSize = 100;

    @Metadata(
              label = "producer",
              description = "How many milliseconds should each producer stay alive in the cache.")
    private int publisherCacheTimeout = 180000;

    @Metadata(
              label = "advanced",
              description = "How many milliseconds should a producer be allowed to terminate.")
    private int publisherTerminationTimeout = 60000;

    private RemovalListener<String, Publisher> removalListener = removal -> {
        Publisher publisher = removal.getValue();
        if (publisher == null) {
            return;
        }
        publisher.shutdown();
        try {
            publisher.awaitTermination(publisherTerminationTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };

    private Cache<String, Publisher> cachedPublishers = CacheBuilder.newBuilder()
            .expireAfterWrite(publisherCacheTimeout, TimeUnit.MILLISECONDS)
            .maximumSize(publisherCacheSize)
            .removalListener(removalListener)
            .build();

    public GooglePubsubComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Google PubSub Endpoint format \"projectId:destinationName[:subscriptionName]\"");
        }

        GooglePubsubEndpoint pubsubEndpoint = new GooglePubsubEndpoint(uri, this, remaining);
        pubsubEndpoint.setProjectId(parts[0]);
        pubsubEndpoint.setDestinationName(parts[1]);
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

    public Publisher getPublisher(String topicName, GooglePubsubEndpoint googlePubsubEndpoint, String serviceAccountKey)
            throws ExecutionException {
        return cachedPublishers.get(topicName, () -> buildPublisher(topicName, googlePubsubEndpoint, serviceAccountKey));
    }

    private Publisher buildPublisher(String topicName, GooglePubsubEndpoint googlePubsubEndpoint, String serviceAccountKey)
            throws IOException {
        Publisher.Builder builder = Publisher.newBuilder(topicName);
        if (StringHelper.trimToNull(endpoint) != null) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
            TransportChannelProvider channelProvider
                    = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            builder.setChannelProvider(channelProvider);
        }
        CredentialsProvider credentialsProvider;
        if (ObjectHelper.isEmpty(serviceAccountKey)) {
            credentialsProvider = NoCredentialsProvider.create();
        } else {
            InputStream serviceAccountFile
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), serviceAccountKey);
            credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(serviceAccountFile));
        }
        builder.setCredentialsProvider(credentialsProvider);
        if (StringHelper.trimToNull(googlePubsubEndpoint.getPubsubEndpoint()) != null) {
            builder.setEndpoint(googlePubsubEndpoint.getPubsubEndpoint());
        }
        if (googlePubsubEndpoint.isMessageOrderingEnabled()) {
            builder.setEnableMessageOrdering(true);
            if (StringHelper.trimToNull(googlePubsubEndpoint.getPubsubEndpoint()) == null) {
                LOG.warn("In conjunction with enabeling message ordering the pubsubEndpoint should be set. "
                         + "Message ordering is only guaranteed when send to the same region.");
            }
        }
        return builder.build();
    }

    public Subscriber getSubscriber(String subscriptionName, MessageReceiver messageReceiver, String serviceAccountKey)
            throws IOException {
        Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, messageReceiver);
        if (StringHelper.trimToNull(endpoint) != null) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
            TransportChannelProvider channelProvider
                    = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            builder.setChannelProvider(channelProvider);
        }
        CredentialsProvider credentialsProvider;
        if (ObjectHelper.isEmpty(serviceAccountKey)) {
            credentialsProvider = NoCredentialsProvider.create();
        } else {
            InputStream serviceAccountFile
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), serviceAccountKey);
            credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(serviceAccountFile));
        }
        builder.setCredentialsProvider(credentialsProvider);
        return builder.build();
    }

    public SubscriberStub getSubscriberStub(String serviceAccountKey) throws IOException {
        SubscriberStubSettings.Builder builder = SubscriberStubSettings.newBuilder().setTransportChannelProvider(
                SubscriberStubSettings.defaultGrpcTransportProviderBuilder().build());

        if (StringHelper.trimToNull(endpoint) != null) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
            TransportChannelProvider channelProvider
                    = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            builder.setTransportChannelProvider(channelProvider);
        }
        CredentialsProvider credentialsProvider;
        if (ObjectHelper.isEmpty(serviceAccountKey)) {
            credentialsProvider = NoCredentialsProvider.create();
        } else {
            InputStream serviceAccountFile
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), serviceAccountKey);
            credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(serviceAccountFile));
        }
        builder.setCredentialsProvider(credentialsProvider);
        return builder.build().createStub();
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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
}
