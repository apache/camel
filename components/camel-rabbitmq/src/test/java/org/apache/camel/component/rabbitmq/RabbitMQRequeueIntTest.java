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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Integration test to confirm REQUEUE header causes message to be re-queued instead of sent to DLQ.
 */
public class RabbitMQRequeueIntTest extends CamelTestSupport {
    public static final String ROUTING_KEY = "rk4";

    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject(uri = "rabbitmq:localhost:5672/ex4?"
            + "autoAck=false&queue=q4&routingKey=" + ROUTING_KEY)
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
                        .inOnly(consumingMockEndpoint)
                        .throwException(new Exception("Simulated exception"));
            }
        };
    }

    @Test
    public void testNoRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBody("Hello, World!");

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testNonBooleanRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, 4L);

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testFalseRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, false);

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testTrueRequeueHeaderCausesRequeue() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.setMinimumExpectedMessageCount(2);

        directProducer.sendBodyAndHeader("Hello, World!", RabbitMQConstants.REQUEUE, true);

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }
}
