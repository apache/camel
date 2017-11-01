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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version
 */
public class SplitterOnCompletionTest extends ContextTestSupport {

    public void testSplitOk() throws Exception {
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World,Bye World");
        getMockEndpoint("mock:split").expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:start", "Hello World,Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testSplitException() throws Exception {
        getMockEndpoint("mock:done").expectedBodiesReceived("Hello World,Kaboom,Bye World");
        getMockEndpoint("mock:split").expectedBodiesReceived("Hello World", "Bye World");

        try {
            template.sendBody("direct:start", "Hello World,Kaboom,Bye World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", iae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().to("log:done", "mock:done");

                from("direct:start")
                    .split(body().tokenize(","))
                        .process(new MyProcessor())
                        .to("mock:split");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Forced");
            }
        }
    }
}
