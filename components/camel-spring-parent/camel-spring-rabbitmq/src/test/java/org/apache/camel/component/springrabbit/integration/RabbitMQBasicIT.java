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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class RabbitMQBasicIT extends RabbitMQITSupport {

    String foo;
    String bar;

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        ConnectionProperties connectionProperties = service.connectionProperties();
        foo = String.format("spring-rabbitmq:%s:%d/foo?username=%s&password=%s", connectionProperties.hostname(),
                connectionProperties.port(), connectionProperties.username(), connectionProperties.password());

        bar = String.format("spring-rabbitmq:%s:%d/bar?username=%s&password=%s", connectionProperties.hostname(),
                connectionProperties.port(), connectionProperties.username(), connectionProperties.password());

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(foo).log("FOO received: ${body}").to(bar);

                from(bar).log("BAR received: ${body}").to(mock).transform().simple("Bye ${body}");
            }
        };
    }

    @Test
    public void sentBasicInOnly() throws Exception {
        mock.expectedBodiesReceived("World");

        log.info("Sending to FOO");
        template.sendBody(foo, "World");
        log.info("Sending to FOO done");

        mock.assertIsSatisfied();
    }

    @Test
    public void sentBasicInOut() throws Exception {
        mock.expectedBodiesReceived("World");

        log.info("Sending to FOO");
        String out = template.requestBody(foo, "World", String.class);
        assertEquals("Bye World", out);
        log.info("Sending to FOO done");

        mock.assertIsSatisfied();
    }

    @Test
    public void sentBasicInOutTwo() throws Exception {
        mock.expectedBodiesReceived("World", "Camel");

        log.info("Sending to FOO");
        String out = template.requestBody(foo, "World", String.class);
        assertEquals("Bye World", out);
        out = template.requestBody(foo, "Camel", String.class);
        assertEquals("Bye Camel", out);
        log.info("Sending to FOO done");

        mock.assertIsSatisfied();
    }

}
