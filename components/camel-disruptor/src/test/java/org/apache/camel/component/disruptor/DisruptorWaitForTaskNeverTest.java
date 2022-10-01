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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DisruptorWaitForTaskNeverTest extends CamelTestSupport {
    @Test
    void testInOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        final String out = template.requestBody("direct:start", "Hello World", String.class);
        // we do not wait for the response so we just get our own input back
        assertEquals("Hello World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        final Exchange out = template.send("direct:start", new Processor() {
            @Override
            public void process(final Exchange exchange) {
                exchange.getIn().setBody("Hello World");
                exchange.setPattern(ExchangePattern.InOnly);
            }
        });
        // we do not wait for the response so we just get our own input back
        assertEquals("Hello World", out.getIn().getBody());

        // Should return the in message as no reply is expected
        assertEquals("Hello World", out.getMessage().getBody());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("disruptor:foo?waitForTaskToComplete=Never");

                from("disruptor:foo?waitForTaskToComplete=Never").transform(constant("Bye World"))
                        .to("mock:result");
            }
        };
    }
}
