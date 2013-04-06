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
public class SplitterStreamingErrorHandlingTest extends ContextTestSupport {

    public void testSplitterStreamingNoError() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("A", "B", "C", "D", "E");
        getMockEndpoint("mock:b").expectedBodiesReceived("A", "B", "C", "D", "E");
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C,D,E");

        template.sendBody("direct:start", "A,B,C,D,E");

        assertMockEndpointsSatisfied();
    }

    public void testSplitterStreamingWithError() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("A", "B", "Kaboom", "D", "E");
        getMockEndpoint("mock:b").expectedBodiesReceived("A", "B", "D", "E");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // we do not stop on exception and thus the splitted message which failed
        // would be silently ignored so we can continue routing
        // you can always use a custom aggregation strategy to deal with errors your-self
        try {
            template.sendBody("direct:start", "A,B,Kaboom,D,E");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Cannot do this", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(",")).streaming()
                        .to("mock:a")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Kaboom".equals(body)) {
                                    throw new IllegalArgumentException("Cannot do this");
                                }
                            }
                        })
                        .to("mock:b")
                    .end()
                    .to("mock:result");
            }
        };
    }
}
