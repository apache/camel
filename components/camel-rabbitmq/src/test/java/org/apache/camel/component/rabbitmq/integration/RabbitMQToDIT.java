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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQComponent;
import org.junit.jupiter.api.Test;

public class RabbitMQToDIT extends AbstractRabbitMQIT {

    @Test
    public void testToD() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello bar");
        getMockEndpoint("mock:beer").expectedBodiesReceived("Hello beer");

        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                RabbitMQComponent mq = context.getComponent("rabbitmq", RabbitMQComponent.class);
                mq.setHostname(service.connectionProperties().hostname());
                mq.setPortNumber(service.connectionProperties().port());
                mq.setUsername(service.connectionProperties().username());
                mq.setPassword(service.connectionProperties().password());

                // route message dynamic using toD
                from("direct:start").toD("rabbitmq:${header.where}");

                from("rabbitmq:bar").to("mock:bar");
                from("rabbitmq:beer").to("mock:beer");
            }
        };
    }

}
