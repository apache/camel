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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;

/**
 * Based on CAMEL-1546
 */
public class AggregatorExceptionHandleTest extends ContextTestSupport {

    @Test
    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        // no error
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "id", 1);
        template.sendBodyAndHeader("direct:start", "Hi World", "id", 1);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHandled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:handled");
        mock.expectedBodiesReceived("Damn");

        // no result
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hi World", "id", 1);
        template.sendBodyAndHeader("direct:start", "Damn", "id", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("mock:handled");

                from("direct:start").aggregate(header("id"), new UseLatestAggregationStrategy()).completionTimeout(100).completionTimeoutCheckerInterval(10)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);
                            if ("Damn".equals(body)) {
                                throw new IllegalArgumentException("Damn");
                            }
                            exchange.getMessage().setBody("Bye World");
                        }
                    }).to("mock:result");

            }
        };
    }
}
