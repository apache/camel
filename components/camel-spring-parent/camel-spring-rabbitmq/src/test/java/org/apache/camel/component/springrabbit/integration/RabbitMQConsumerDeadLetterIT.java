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
package org.apache.camel.component.springrabbit.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * A fanout dead letter exchange needs no deadLetterRoutingKey: the dead letter queue is bound with an empty routing
 * key.
 */
public class RabbitMQConsumerDeadLetterIT extends RabbitMQITSupport {

    @Test
    public void testDeadLetterQueueWithoutDeadLetterRoutingKey() throws Exception {
        getMockEndpoint("mock:dead").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("spring-rabbitmq:" + uniqueName("orders") + "?routingKey=new");

                from("spring-rabbitmq:" + uniqueName("orders") + "?queues=" + uniqueName("orders") + "&routingKey=new"
                     + "&maximumRetryAttempts=0&deadLetterExchange=" + uniqueName("dlx")
                     + "&deadLetterExchangeType=fanout&deadLetterQueue=" + uniqueName("dlq"))
                        .throwException(new IllegalStateException("Forced"));

                // the dead letter exchange and queue are declared by the route above
                from("spring-rabbitmq:" + uniqueName("dlx") + "?queues=" + uniqueName("dlq") + "&autoDeclare=false")
                        .to("mock:dead");
            }
        };
    }
}
