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
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

public class PubsubTestSupport extends CamelTestSupport {

    public static final String PROJECT_ID;

    static {
        Properties testProperties = loadProperties();
        PROJECT_ID = testProperties.getProperty("project.id");
    }

    @ClassRule
    public static GenericContainer container = new GenericContainer("google/cloud-sdk:latest")
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
        ManagedChannel channel = null;
        TopicAdminClient topicAdminClient = null;
        SubscriptionAdminClient subscriptionAdminClient = null;

        try {
            Integer port = container.getFirstMappedPort();
            channel = ManagedChannelBuilder
                    .forTarget(String.format("%s:%s", "localhost", port))
                    .usePlaintext()
                    .build();

            FixedTransportChannelProvider channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

            ProjectTopicName projectTopicName = ProjectTopicName.of(PROJECT_ID, topicName);
            ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, subscriptionName);

            topicAdminClient = TopicAdminClient.create(
                    TopicAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
            topicAdminClient.createTopic(projectTopicName);

            subscriptionAdminClient = SubscriptionAdminClient.create(
                    SubscriptionAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
            subscriptionAdminClient.createSubscription(projectSubscriptionName, projectTopicName,
                    PushConfig.getDefaultInstance(), ackDeadlineSeconds);

        } catch (Exception ignored) {
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
            if (topicAdminClient != null) {
                topicAdminClient.shutdown();
            }
            if (subscriptionAdminClient != null) {
                subscriptionAdminClient.shutdown();
            }
        }
    }
}
