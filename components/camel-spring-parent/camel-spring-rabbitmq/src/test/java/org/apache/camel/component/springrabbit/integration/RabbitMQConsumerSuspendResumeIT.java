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

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route that is suspended and resumed consumes again, with either type of listener container: while it is suspended,
 * the queue has no consumer and keeps the message sent meanwhile, which is consumed once the route is resumed.
 */
public class RabbitMQConsumerSuspendResumeIT extends RabbitMQITSupport {

    // the test checks the queue with its own connection factory
    private CachingConnectionFactory testConnectionFactory;

    @AfterEach
    void destroyTestConnectionFactory() {
        if (testConnectionFactory != null) {
            testConnectionFactory.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "DMLC", "SMLC" })
    public void testSuspendAndResume(String type) throws Exception {
        String queue = uniqueName(type + "-queue");
        testConnectionFactory = (CachingConnectionFactory) createConnectionFactory(false);
        RabbitAdmin admin = new RabbitAdmin(testConnectionFactory);
        MockEndpoint mock = getMockEndpoint("mock:" + type);
        mock.expectedBodiesReceived("order 1");
        template.sendBody("direct:" + type, "order 1");
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        context.getRouteController().suspendRoute(type);

        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus(type));
        mock.reset();
        mock.expectedBodiesReceived("order 2");
        template.sendBody("direct:" + type, "order 2");
        // the message waits in the queue, which has no consumer
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> count(admin, queue, RabbitAdmin.QUEUE_MESSAGE_COUNT) == 1
                        && count(admin, queue, RabbitAdmin.QUEUE_CONSUMER_COUNT) == 0);

        context.getRouteController().resumeRoute(type);

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(type));
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private static int count(RabbitAdmin admin, String queue, Object property) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? -1 : ((Number) properties.get(property)).intValue();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String type : new String[] { "DMLC", "SMLC" }) {
                    from("direct:" + type)
                            .to("spring-rabbitmq:" + uniqueName(type) + "?routingKey=new");

                    from("spring-rabbitmq:" + uniqueName(type) + "?queues=" + uniqueName(type + "-queue")
                         + "&routingKey=new&messageListenerContainerType=" + type)
                            .routeId(type)
                            .to("mock:" + type);
                }
            }
        };
    }
}
