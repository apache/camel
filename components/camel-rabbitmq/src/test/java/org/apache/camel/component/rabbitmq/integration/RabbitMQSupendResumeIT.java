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
package org.apache.camel.component.rabbitmq.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.Test;

public class RabbitMQSupendResumeIT extends AbstractRabbitMQIT {
    private static final String EXCHANGE = "ex6";

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Produce("direct:start")
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();
        String rabbitMQEndpoint = String.format(
                "rabbitmq:localhost:%d/%susername=%s&password=%s&queue=q6&routingKey=rk3&autoDelete=false",
                connectionProperties.port(), connectionProperties.username(), connectionProperties.password(), EXCHANGE);

        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").routeId("producer").log("sending ${body}").to(rabbitMQEndpoint);
                from(rabbitMQEndpoint).routeId("consumer").log("got ${body}").to("mock:result");
            }
        };
    }

    @Test
    public void testSuspendedResume() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("hello");

        template.sendBody("hello");

        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().resumeRoute("consumer");

        // sleep a bit to ensure its properly suspended
        Thread.sleep(2000);

        resetMocks();
        resultEndpoint.expectedMessageCount(0);

        template.sendBody("Hello2");

        MockEndpoint.assertIsSatisfied(context, 1, TimeUnit.SECONDS);

        resetMocks();
        resultEndpoint.expectedBodiesReceived("Hello2");
        resultEndpoint.expectedMessageCount(1);

        context.getRouteController().resumeRoute("consumer");

        MockEndpoint.assertIsSatisfied(context);
    }

}
