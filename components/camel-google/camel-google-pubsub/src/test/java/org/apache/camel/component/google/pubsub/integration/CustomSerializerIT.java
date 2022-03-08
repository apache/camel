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

import java.nio.charset.StandardCharsets;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.google.pubsub.serializer.GooglePubsubSerializer;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class CustomSerializerIT extends PubsubTestSupport {

    private static final String TOPIC_NAME = "typesSend";
    private static final String SUBSCRIPTION_NAME = "TypesReceive";

    @EndpointInject("direct:from")
    private Endpoint directIn;

    @EndpointInject("google-pubsub:{{project.id}}:" + TOPIC_NAME)
    private Endpoint pubsubTopic;

    @EndpointInject("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=true")
    private Endpoint pubsubSubscription;

    @EndpointInject("mock:receiveResult")
    private MockEndpoint receiveResult;

    @Produce("direct:from")
    private ProducerTemplate producer;

    @BindToRegistry
    private GooglePubsubSerializer serializer = new CustomSerializer();

    @Override
    public void createTopicSubscription() {
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(directIn).to(pubsubTopic);

                from(pubsubSubscription).to(receiveResult);
            }
        };
    }

    @Test
    public void customSerializer() throws Exception {
        receiveResult.expectedBodiesReceived("12345 custom serialized".getBytes(StandardCharsets.UTF_8));

        producer.sendBody(12345);

        receiveResult.assertIsSatisfied();
    }

    private static final class CustomSerializer implements GooglePubsubSerializer {

        @Override
        public byte[] serialize(Object payload) {
            // Append 'custom serialized' to the payload
            String serialized = payload + " custom serialized";
            return serialized.getBytes(StandardCharsets.UTF_8);
        }
    }
}
