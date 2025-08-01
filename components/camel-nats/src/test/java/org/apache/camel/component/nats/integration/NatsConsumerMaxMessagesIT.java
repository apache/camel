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
package org.apache.camel.component.nats.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
public class NatsConsumerMaxMessagesIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testMaxConsumer() throws InterruptedException {
        mockResultEndpoint.setExpectedMessageCount(5);
        template.sendBody("direct:send", "test");
        template.sendBody("direct:send", "test1");
        template.sendBody("direct:send", "test2");
        template.sendBody("direct:send", "test3");
        template.sendBody("direct:send", "test4");
        template.sendBody("direct:send", "test5");
        template.sendBody("direct:send", "test6");
        template.sendBody("direct:send", "test7");
        template.sendBody("direct:send", "test8");
        template.sendBody("direct:send", "test9");
        template.sendBody("direct:send", "test10");

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send").to("nats:test");

                from("nats:test?maxMessages=5").to(mockResultEndpoint);
            }
        };
    }
}
