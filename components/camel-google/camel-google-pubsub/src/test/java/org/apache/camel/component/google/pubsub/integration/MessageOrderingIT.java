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
package org.apache.camel.component.google.pubsub.integration;

import java.util.Arrays;
import java.util.List;

import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.google.pubsub.GooglePubsubConstants.ORDERING_KEY;

@Disabled("TODO fix me: CAMEL-20374")
class MessageOrderingIT extends PubsubTestSupport {

    private static final String TOPIC_NAME = "camel.input-topic";
    private static final String SUBSCRIPTION_NAME = "camel.input-topic-subscription";

    @EndpointInject("direct:in")
    private Endpoint directIn;

    @EndpointInject("google-pubsub:{{project.id}}:" + TOPIC_NAME
                    + "?messageOrderingEnabled=true&pubsubEndpoint=us-east1-pubsub.googleapis.com:443")
    private Endpoint pubsubTopic;

    @EndpointInject("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME)
    private Endpoint pubsubSubscription;

    @EndpointInject("mock:input")
    private MockEndpoint inputMock;

    @EndpointInject("mock:output")
    private MockEndpoint outputMock;

    @Produce("direct:in")
    private ProducerTemplate producer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(directIn).routeId("directRoute")
                        .setHeader(ORDERING_KEY, constant("orderkey"))
                        .to(pubsubTopic)
                        .to(inputMock);

                from(pubsubSubscription).routeId("subscriptionRoute")
                        .autoStartup(false)
                        .to(outputMock);
            }
        };
    }

    @Override
    public void createTopicSubscription() {
        TopicName inputTopicName = TopicName.of(PROJECT_ID, TOPIC_NAME);
        ProjectSubscriptionName projectInputSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_NAME);
        Topic inputTopic = Topic.newBuilder().setName(inputTopicName.toString()).build();
        Subscription inputSubscription = Subscription.newBuilder()
                .setName(projectInputSubscriptionName.toString())
                .setTopic(inputTopic.getName())
                .setEnableMessageOrdering(true)
                .build();
        createTopicSubscriptionPair(inputTopic, inputSubscription);
    }

    @Test
    void orderedMessageDeliveryTest() throws Exception {
        List<String> bodyList = Arrays.asList("1", "2", "3", "4", "5", "6");
        inputMock.expectedMessageCount(6);
        outputMock.expectedMessageCount(6);
        for (String string : bodyList) {
            producer.sendBody(string);
        }
        inputMock.assertIsSatisfied();
        context.getRouteController().startRoute("subscriptionRoute");
        outputMock.expectedBodiesReceived(bodyList);
        outputMock.assertIsSatisfied();
    }
}
