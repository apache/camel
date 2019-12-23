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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SplitterNoAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testSplitNoAggregationStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Hello World", "Bye World", "Hi World");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World,Bye World,Hi World");

        template.sendBody("direct:start", "Hello World,Bye World,Hi World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitNoAggregationStrategyException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Hello World", "Bye World", "Hi World");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World,Kaboom,Bye World,Hi World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body().tokenize(",")).process(new MyProcessor()).to("mock:split").end().to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Forced");
            }
        }
    }
}
