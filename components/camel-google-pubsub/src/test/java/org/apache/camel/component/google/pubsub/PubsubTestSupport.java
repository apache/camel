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

import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;

import java.io.InputStream;
import java.util.Properties;

public class PubsubTestSupport extends CamelTestSupport {

    public static final String SERVICE_KEY;
    public static final String SERVICE_ACCOUNT;
    public static final String PROJECT_ID;
    public static final String SERVICE_URL;

    static {
        Properties testProperties = loadProperties();
        SERVICE_KEY = testProperties.getProperty("service.key");
        SERVICE_ACCOUNT = testProperties.getProperty("service.account");
        PROJECT_ID = testProperties.getProperty("project.id");
        SERVICE_URL = testProperties.getProperty("test.serviceURL");
    }

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

        context.addComponent("google-pubsub", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        addPubsubComponent(context);
        return context;
    }

    public static void createTopicSubscriptionPair(String topicName, String subscriptionName) throws Exception {
        createTopicSubscriptionPair(topicName, subscriptionName, 10);
    }

    public static void createTopicSubscriptionPair(String topicName, String subscriptionName, int ackDeadlineSeconds) throws Exception {

        ProjectTopicName projectTopicName = ProjectTopicName.of(PubsubTestSupport.PROJECT_ID, topicName);
        ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(PubsubTestSupport.PROJECT_ID, subscriptionName);

        try {
            TopicAdminClient.create().createTopic(projectTopicName);
        } catch (Exception ignore) {
        }

        try {
            SubscriptionAdminClient.create().createSubscription(projectSubscriptionName, projectTopicName, PushConfig.getDefaultInstance(), ackDeadlineSeconds);
        } catch (Exception ignore) {
        }
    }
}
