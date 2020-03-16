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
package org.apache.camel.component.nsq;

import java.util.concurrent.TimeoutException;

import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.exceptions.NSQException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NsqConsumerTest extends NsqTestSupport {

    private static final int NUMBER_OF_MESSAGES = 10000;
    private static final String TOPIC = "test";

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testConsumer() throws NSQException, TimeoutException, InterruptedException {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.setAssertPeriod(5000);

        NSQProducer producer = new NSQProducer();
        producer.addAddress("localhost", 4150);
        producer.start();

        producer.produce(TOPIC, "Hello NSQ!".getBytes());

        mockResultEndpoint.assertIsSatisfied();

        assertEquals("Hello NSQ!", mockResultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testLoadConsumer() throws NSQException, TimeoutException, InterruptedException {
        mockResultEndpoint.setExpectedMessageCount(NUMBER_OF_MESSAGES);
        mockResultEndpoint.setAssertPeriod(5000);

        NSQProducer producer = new NSQProducer();
        producer.addAddress("localhost", 4150);
        producer.start();

        for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.produce(TOPIC, (String.format("Hello NSQ%d!", i)).getBytes());
        }

        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRequeue() throws NSQException, TimeoutException, InterruptedException {
        mockResultEndpoint.setExpectedMessageCount(1);
        mockResultEndpoint.setAssertPeriod(5000);

        NSQProducer producer = new NSQProducer();
        producer.addAddress("localhost", 4150);
        producer.start();

        producer.produce(TOPIC, "Test Requeue".getBytes());

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("nsq://%s?servers=%s&lookupInterval=2000&autoFinish=false&requeueInterval=1000", TOPIC, getNsqConsumerUrl()).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String messageText = exchange.getIn().getBody(String.class);
                        int attempts = exchange.getIn().getHeader(NsqConstants.NSQ_MESSAGE_ATTEMPTS, Integer.class);
                        if (messageText.contains("Requeue") && attempts < 3) {
                            throw new Exception("Forced error");
                        }
                    }
                }).to(mockResultEndpoint);
            }
        };
    }
}
