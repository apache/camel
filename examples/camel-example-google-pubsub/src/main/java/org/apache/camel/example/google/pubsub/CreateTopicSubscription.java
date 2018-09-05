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
package org.apache.camel.example.google.pubsub;

import java.util.Properties;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.Subscription;
import com.google.api.services.pubsub.model.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreateTopicSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(CreateTopicSubscription.class);

    private CreateTopicSubscription() {
    }

    public static void main(String[] args) throws Exception {
        createTopicSubscriptionPair(10);
    }

    private static void createTopicSubscriptionPair(int ackDeadlineSeconds) throws Exception {
        Properties properties = PubsubUtil.loadProperties();
        String projectId = properties.getProperty("pubsub.projectId");
        String topic = properties.getProperty("pubsub.topic");
        String subscriptionName = properties.getProperty("pubsub.subscription");

        String topicFullName = String.format("projects/%s/topics/%s",
                projectId,
                topic);

        String subscriptionFullName = String.format("projects/%s/subscriptions/%s",
                projectId,
                subscriptionName);

        Pubsub pubsub = PubsubUtil
                .createConnectionFactory(properties)
                .getDefaultClient();

        try {
            pubsub.projects()
                    .topics()
                    .create(topicFullName, new Topic())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // 409 indicates that the resource is available already
            if (409 == e.getStatusCode()) {
                LOG.info("Topic " + topic + " already exist");
            } else {
                throw e;
            }
        }

        try {
            Subscription subscription = new Subscription()
                    .setTopic(topicFullName)
                    .setAckDeadlineSeconds(ackDeadlineSeconds);

            pubsub.projects()
                    .subscriptions()
                    .create(subscriptionFullName, subscription)
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // 409 indicates that the resource is available already
            if (409 == e.getStatusCode()) {
                LOG.info("Subscription " + subscriptionName + " already exist");
            } else {
                throw e;
            }
        }
    }

}
