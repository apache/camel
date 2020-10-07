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
import java.util.Properties;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

public class PubsubTestSupport extends ContainerAwareTestSupport {

    public static final String PROJECT_ID;

    static {
        Properties testProperties = loadProperties();
        PROJECT_ID = testProperties.getProperty("project.id");
    }

    protected GenericContainer<?> container = new GenericContainer<>("google/cloud-sdk:latest")
            .withExposedPorts(8383)
            .withCommand("/bin/sh", "-c",
                    String.format("gcloud beta emulators pubsub start --project %s --host-port=0.0.0.0:%d",
                            PROJECT_ID, 8383))
            .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*started.*$"));

    private static Properties loadProperties() {
        Properties testProperties = new Properties();
        InputStream fileIn = PubsubTestSupport.class.getClassLoader().getResourceAsStream("simple.properties");
        try {
            testProperties.load(fileIn);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return testProperties;
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return super.createContainer();
    }

    protected void addPubsubComponent(CamelContext context) {

        GooglePubsubComponent component = new GooglePubsubComponent();
        component.setEndpoint(container.getContainerIpAddress() + ":" + container.getFirstMappedPort());

        context.addComponent("google-pubsub", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @BindToRegistry("prop")
    public Properties loadRegProperties() throws Exception {
        return loadProperties();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        container.start();
        createTopicSubscription();
        CamelContext context = super.createCamelContext();
        addPubsubComponent(context);
        return context;
    }

    public void createTopicSubscription() throws Exception {
    }

    public void createTopicSubscriptionPair(String topicName, String subscriptionName) {
        createTopicSubscriptionPair(topicName, subscriptionName, 10);
    }

    public void createTopicSubscriptionPair(String topicName, String subscriptionName, int ackDeadlineSeconds) {
        TopicName projectTopicName = TopicName.of(PROJECT_ID, topicName);
        ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, subscriptionName);

        Topic topic = Topic.newBuilder().setName(projectTopicName.toString()).build();
        Subscription subscription = Subscription.newBuilder()
                .setName(projectSubscriptionName.toString())
                .setTopic(topic.getName())
                .setAckDeadlineSeconds(ackDeadlineSeconds)
                .build();

        createTopicSubscriptionPair(topic, subscription);
    }

    public void createTopicSubscriptionPair(Topic topic, Subscription subscription) {
        createTopic(topic);
        createSubscription(subscription);
    }

    public void createTopic(Topic topic) {
        TopicAdminClient topicAdminClient = createTopicAdminClient();

        topicAdminClient.createTopic(topic);

        topicAdminClient.shutdown();
    }

    public void createSubscription(Subscription subscription) {
        SubscriptionAdminClient subscriptionAdminClient = createSubscriptionAdminClient();

        subscriptionAdminClient.createSubscription(subscription);

        subscriptionAdminClient.shutdown();
    }

    private FixedTransportChannelProvider createChannelProvider() {
        Integer port = container.getFirstMappedPort();
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(String.format("%s:%s", "localhost", port))
                .usePlaintext()
                .build();

        return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    }

    private TopicAdminClient createTopicAdminClient() {
        FixedTransportChannelProvider channelProvider = createChannelProvider();
        CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

        try {
            return TopicAdminClient.create(
                    TopicAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SubscriptionAdminClient createSubscriptionAdminClient() {
        FixedTransportChannelProvider channelProvider = createChannelProvider();
        CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

        try {
            return SubscriptionAdminClient.create(
                    SubscriptionAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
