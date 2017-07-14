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
package org.apache.camel.component.mqtt;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.Test;

public class MQTTConsumerMultipleTopicsTest extends MQTTBaseTest {

    @Test
    public void testConsumeMultipleTopics() throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setHost(MQTTTestSupport.getHostForMQTTEndpoint());
        BlockingConnection publisherConnection = mqtt.blockingConnection();
        Topic topic1 = new Topic(TEST_TOPIC, QoS.AT_MOST_ONCE);
        Topic topic2 = new Topic(TEST_TOPIC_2, QoS.AT_MOST_ONCE);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(numberOfMessages * 2);

        publisherConnection.connect();
        String payload;
        for (int i = 0; i < numberOfMessages; i++) {
            payload = "Topic 1, Message " + i;
            publisherConnection.publish(topic1.name().toString(), payload.getBytes(), QoS.AT_LEAST_ONCE, false);
            payload = "Topic 2, Message " + i;
            publisherConnection.publish(topic2.name().toString(), payload.getBytes(), QoS.AT_LEAST_ONCE, false);
        }

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                from("mqtt:bar?subscribeTopicNames=" + TEST_TOPICS + "&host=" + MQTTTestSupport.getHostForMQTTEndpoint())
                    .transform(body().convertToString())
                    .to("mock:result");
            }
        };
    }
}
