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
package org.apache.camel.component.rabbitmq;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Integration test to check that RabbitMQ Endpoint is able handle heavy load using multiple producers and
 * consumers
 */
public class RabbitMQLoadIntTest extends CamelTestSupport {
    public static final String ROUTING_KEY = "rk4";
    private static final int PRODUCER_COUNT = 10;
    private static final int CONSUMER_COUNT = 10;
    private static final int MESSAGE_COUNT = 100;
    
    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject(uri = "rabbitmq:localhost:5672/ex4?username=cameltest&password=cameltest"
                          + "&queue=q4&routingKey=" + ROUTING_KEY + "&threadPoolSize=" + (CONSUMER_COUNT + 5)
                          + "&concurrentConsumers=" + CONSUMER_COUNT)
    private Endpoint rabbitMQEndpoint;

    @EndpointInject(uri = "mock:producing")
    private MockEndpoint producingMockEndpoint;

    @EndpointInject(uri = "mock:consuming")
    private MockEndpoint consumingMockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:rabbitMQ")
                        .id("producingRoute")
                        .log("Sending message")
                        .inOnly(rabbitMQEndpoint)
                        .to(producingMockEndpoint);
                from(rabbitMQEndpoint)
                        .id("consumingRoute")
                        .log("Receiving message")
                        .to(consumingMockEndpoint);
            }
        };
    }

    @Test
    public void testSendEndReceive() throws Exception {
        // Start producers
        ExecutorService executorService = Executors.newFixedThreadPool(PRODUCER_COUNT);
        List<Future<?>> futures = new ArrayList<>(PRODUCER_COUNT);
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < MESSAGE_COUNT; i++) {
                        directProducer.sendBodyAndHeader("Message #" + i, RabbitMQConstants.ROUTING_KEY,
                                                         ROUTING_KEY);
                    }
                }
            }));
        }
        // Wait for producers to end
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        // Check message count
        producingMockEndpoint.expectedMessageCount(PRODUCER_COUNT * MESSAGE_COUNT);
        consumingMockEndpoint.expectedMessageCount(PRODUCER_COUNT * MESSAGE_COUNT);
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }
}
