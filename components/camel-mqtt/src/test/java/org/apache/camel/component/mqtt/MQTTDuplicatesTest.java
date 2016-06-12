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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests duplicate delivery via mqtt consumer.
 * 
 * @version
 */
public class MQTTDuplicatesTest extends MQTTBaseTest {

    private static final int MESSAGE_COUNT = 50;
    private static final int WAIT_MILLIS = 100;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:withClientID")
    protected ProducerTemplate templateWithClientID;

    @Produce(uri = "direct:withoutClientID")
    protected ProducerTemplate templateWithoutClientID;

    @Test
    public void testMqttDuplicates() throws Exception {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = System.currentTimeMillis() + ": Dummy! " + i;
            templateWithClientID.asyncSendBody("direct:withClientID", body);
            Thread.sleep(WAIT_MILLIS);
        }

        assertNoDuplicates();
    }

    @Test
    public void testMqttDuplicatesAfterBrokerRestartWithoutClientID() throws Exception {
        brokerService.stop();
        brokerService.waitUntilStopped();

        LOG.info(">>>>>>>>>> Restarting broker...");
        brokerService = new BrokerService();
        brokerService.setPersistent(false);
        brokerService.setAdvisorySupport(false);
        brokerService.addConnector(MQTTTestSupport.getConnection() + "?trace=true");
        brokerService.start();
        brokerService.waitUntilStarted();
        LOG.info(">>>>>>>>>> Broker restarted");

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = System.currentTimeMillis() + ": Dummy-restart-without-clientID! " + i;
            templateWithoutClientID.asyncSendBody("direct:withoutClientID", body);
            Thread.sleep(WAIT_MILLIS);
        }

        assertNoDuplicates();
    }

    @Test
    public void testMqttDuplicatesAfterBrokerRestartWithClientID() throws Exception {
        brokerService.stop();
        brokerService.waitUntilStopped();

        LOG.info(">>>>>>>>>> Restarting broker...");
        brokerService = new BrokerService();
        brokerService.setPersistent(false);
        brokerService.setAdvisorySupport(false);
        brokerService.addConnector(MQTTTestSupport.getConnection() + "?trace=true");
        brokerService.start();
        brokerService.waitUntilStarted();
        LOG.info(">>>>>>>>>> Broker restarted");

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String body = System.currentTimeMillis() + ": Dummy-restart-with-clientID! " + i;
            templateWithClientID.asyncSendBody("direct:withClientID", body);
            Thread.sleep(WAIT_MILLIS);
        }

        assertNoDuplicates();
    }

    private void assertNoDuplicates() {
        List<Exchange> exchanges = resultEndpoint.getExchanges();
        Assert.assertTrue("No message was delivered - something wrong happened", exchanges.size() > 0);
        Set<String> values = new HashSet<String>();
        List<String> duplicates = new ArrayList<String>();
        for (Exchange e : exchanges) {
            String body = e.getIn().getBody(String.class);
            if (values.contains(body)) {
                duplicates.add(body);
            }
            values.add(body);
        }
        Assert.assertTrue("Duplicate messages are detected: " + duplicates.toString(), duplicates.isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // --------------------
                //  Without client ID:
                // --------------------
                from("direct:withoutClientID")
                    .routeId("SenderWithoutClientID")
                    .log("$$$$$ Sending message: ${body}")
                    .to("mqtt:sender?publishTopicName=test/topic1&qualityOfService=ExactlyOnce&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
            
                from("mqtt:reader?subscribeTopicName=test/topic1&qualityOfService=ExactlyOnce&host=" + MQTTTestSupport.getHostForMQTTEndpoint())
                    .routeId("ReceiverWithoutClientID")
                    .log("$$$$$ Received message: ${body}")
                    .to("mock:result");

                // --------------------
                //  With client ID:
                // --------------------
                from("direct:withClientID")
                    .routeId("SenderWithClientID")
                    .log("$$$$$ Sending message: ${body}")
                    .to("mqtt:sender?publishTopicName=test/topic2&clientId=sender&qualityOfService=ExactlyOnce&host=" + MQTTTestSupport.getHostForMQTTEndpoint());
                
                from("mqtt:reader?subscribeTopicName=test/topic2&clientId=receiver&qualityOfService=ExactlyOnce&host=" + MQTTTestSupport.getHostForMQTTEndpoint())
                    .routeId("ReceiverWithClientID")
                    .log("$$$$$ Received message: ${body}")
                    .to("mock:result");
            }
        };
    }
}
