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
import java.util.List;
import java.util.Properties;
import java.util.logging.LogManager;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannelBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.test.infra.google.pubsub.services.GooglePubSubService;
import org.apache.camel.test.infra.google.pubsub.services.GooglePubSubServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubsubTestSupport extends CamelTestSupport {
    @RegisterExtension
    public static GooglePubSubService service = GooglePubSubServiceFactory.createService();

    public static final String PROJECT_ID;

    private static final OrphanChannelLogAppender ORPHAN_APPENDER = new OrphanChannelLogAppender();

    static {
        Properties testProperties = loadProperties();
        PROJECT_ID = testProperties.getProperty("project.id");

        try (InputStream is = PubsubTestSupport.class.getClassLoader().getResourceAsStream("logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            Logger logger = LoggerFactory.getLogger(PubsubTestSupport.class);

            logger.warn(
                    "Unable to setup JUL-to-slf4j logging bridge. The test execution should result in a log of bogus output. Error: {}",
                    e.getMessage(), e);
        }

        org.apache.logging.log4j.core.Logger orphanLogger
                = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                        .getLogger("io.grpc.internal.ManagedChannelOrphanWrapper");
        orphanLogger.addAppender(ORPHAN_APPENDER);
    }

    private static Properties loadProperties() {
        return TestSupport.loadExternalPropertiesQuietly(PubsubTestSupport.class.getClassLoader(), "simple.properties");
    }

    protected void addPubsubComponent(CamelContext context) {

        GooglePubsubComponent component = new GooglePubsubComponent();
        component.setEndpoint(service.getServiceAddress());
        component.setAuthenticate(false);

        context.addComponent("google-pubsub", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @BindToRegistry("prop")
    public Properties loadRegProperties() {
        return loadProperties();
    }

    @Override
    protected void setupResources() throws Exception {
        ORPHAN_APPENDER.reset();
        super.setupResources();
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();
        System.gc();
        List<String> orphans = ORPHAN_APPENDER.getMessages();
        if (!orphans.isEmpty()) {
            Assertions.fail("gRPC channel(s) garbage collected without shutdown (ManagedChannelOrphanWrapper):\n"
                            + String.join("\n", orphans));
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        createTopicSubscription();
        CamelContext context = super.createCamelContext();
        addPubsubComponent(context);
        return context;
    }

    public void createTopicSubscription() {
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

    private TransportChannelProvider createChannelProvider() {

        return InstantiatingGrpcChannelProvider.newBuilder()
                .setEndpoint(service.getServiceAddress())
                .setChannelConfigurator(ManagedChannelBuilder::usePlaintext)
                .build();
    }

    private TopicAdminClient createTopicAdminClient() {
        TransportChannelProvider channelProvider = createChannelProvider();
        CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

        try {
            return TopicAdminClient.create(
                    TopicAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    private SubscriptionAdminClient createSubscriptionAdminClient() {
        TransportChannelProvider channelProvider = createChannelProvider();
        CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

        try {
            return SubscriptionAdminClient.create(
                    SubscriptionAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider)
                            .build());
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
