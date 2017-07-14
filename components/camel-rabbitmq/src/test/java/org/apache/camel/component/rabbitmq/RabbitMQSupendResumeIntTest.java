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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RabbitMQSupendResumeIntTest extends CamelTestSupport {
    private static final String EXCHANGE = "ex6";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?username=cameltest&password=cameltest&queue=q6&routingKey=rk3&autoDelete=false")
    private Endpoint rabbitMQEndpoint;

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
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

        assertMockEndpointsSatisfied();

        context.suspendRoute("consumer");

        // sleep a bit to ensure its properly suspended
        Thread.sleep(2000);

        resetMocks();
        resultEndpoint.expectedMessageCount(0);

        template.sendBody("Hello2");

        assertMockEndpointsSatisfied(1, TimeUnit.SECONDS);

        resetMocks();
        resultEndpoint.expectedBodiesReceived("Hello2");
        resultEndpoint.expectedMessageCount(1);

        context.resumeRoute("consumer");

        assertMockEndpointsSatisfied();
    }

}
