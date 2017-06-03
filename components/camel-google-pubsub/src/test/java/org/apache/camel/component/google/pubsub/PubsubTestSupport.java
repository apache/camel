/**
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.Subscription;
import com.google.api.services.pubsub.model.Topic;
import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

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

        GooglePubsubConnectionFactory cf = new GooglePubsubConnectionFactory()
            .setServiceAccount(SERVICE_ACCOUNT)
            .setServiceAccountKey(SERVICE_KEY)
            .setServiceURL(SERVICE_URL);

        GooglePubsubComponent component = new GooglePubsubComponent();
        component.setConnectionFactory(cf);

        context.addComponent("google-pubsub", component);
        context.addComponent("properties", new PropertiesComponent("ref:prop"));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("prop", loadProperties());
        return jndi;
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

    public static void createTopicSubscriptionPair(String topicName, String subscriptionName, int ackDealineSeconds) throws Exception {

        Pubsub pubsub = new GooglePubsubConnectionFactory()
            .setServiceAccount(SERVICE_ACCOUNT)
            .setServiceAccountKey(SERVICE_KEY)
            .setServiceURL(SERVICE_URL)
            .getDefaultClient();

        String topicFullName = String.format("projects/%s/topics/%s",
                                         PubsubTestSupport.PROJECT_ID,
                                         topicName);

        String subscriptionFullName = String.format("projects/%s/subscriptions/%s",
                                                PubsubTestSupport.PROJECT_ID,
                                                subscriptionName);

        try {
            pubsub.projects()
                  .topics()
                  .create(topicFullName, new Topic())
                  .execute();
        } catch (Exception e) {
            handleAlreadyExistsException(e);
        }

        try {
            Subscription subscription = new Subscription()
                    .setTopic(topicFullName)
                    .setAckDeadlineSeconds(ackDealineSeconds);

            pubsub.projects()
                  .subscriptions()
                  .create(subscriptionFullName, subscription)
                  .execute();
        } catch (Exception e) {
            handleAlreadyExistsException(e);
        }
    }

    private static void handleAlreadyExistsException(Exception e) throws Exception {
        if (e instanceof GoogleJsonResponseException) {
            GoogleJsonResponseException exc = (GoogleJsonResponseException) e;
            // 409 indicates that the resource is available already
            if (409 == exc.getStatusCode()) {
                return;
            }
        }
        throw e;
    }
}
