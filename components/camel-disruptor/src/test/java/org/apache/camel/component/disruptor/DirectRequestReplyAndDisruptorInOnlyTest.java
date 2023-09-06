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
package org.apache.camel.component.disruptor;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirectRequestReplyAndDisruptorInOnlyTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DirectRequestReplyAndDisruptorInOnlyTest.class);

    @Test
    void testInOut() throws Exception {
        getMockEndpoint("mock:log").expectedBodiesReceived("Logging: Bye World");

        final String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
        LOG.info("Got reply {}", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // send the message as InOnly to DISRUPTOR as we want to continue routing
                // (as we don't want to do request/reply over DISRUPTOR)
                // In EIP patterns the WireTap pattern is what this would be
                from("direct:start").transform(constant("Bye World")).to(ExchangePattern.InOnly, "disruptor:log");

                from("disruptor:log").delay(1000).transform(body().prepend("Logging: "))
                        .to("log:log", "mock:log");
            }
        };
    }
}
