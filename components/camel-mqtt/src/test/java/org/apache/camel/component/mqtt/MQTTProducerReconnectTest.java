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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.Test;

public class MQTTProducerReconnectTest extends MQTTBaseTest {

    @Test
    public void testProduce() throws Exception {
        MQTT mqtt = new MQTT();
        mqtt.setHost(MQTTTestSupport.getHostForMQTTEndpoint());
        final BlockingConnection subscribeConnection = mqtt.blockingConnection();
        subscribeConnection.connect();
        Topic topic = new Topic(TEST_TOPIC, QoS.AT_MOST_ONCE);
        Topic[] topics = {topic};
        subscribeConnection.subscribe(topics);
        final CountDownLatch latch = new CountDownLatch(numberOfMessages);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < numberOfMessages; i++) {
                    try {
                        Message message = subscribeConnection.receive();
                        message.ack();
                        latch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        });
        thread.start();

        for (int i = 0; i < numberOfMessages; i++) {

            if (i == 5) {
                LOG.info("Stopping MQTT transport");
                brokerService.getTransportConnectorByScheme("mqtt").stop();

                Thread starter = new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(3000);
                            LOG.info("Starting MQTT transport again");
                            brokerService.getTransportConnectorByScheme("mqtt").start();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                });
                starter.start();
            }

            try {
                template.sendBody("direct:foo", "test message " + i);
            } catch (Exception e) {
                // ignore
            }
        }

        latch.await(20, TimeUnit.SECONDS);
        assertTrue("Messages not consumed = " + latch.getCount(), latch.getCount() == 0);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:foo").to("mqtt:boo?reconnectDelay=1000&publishTopicName=" + TEST_TOPIC + "&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
            }
        };
    }
}
