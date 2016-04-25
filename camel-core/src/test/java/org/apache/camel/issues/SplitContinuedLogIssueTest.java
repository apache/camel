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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class SplitContinuedLogIssueTest extends ContextTestSupport {

    public void testFooBar() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("bar");
        getMockEndpoint("mock:line").expectedBodiesReceived("foo", "bar");
        getMockEndpoint("mock:result").expectedBodiesReceived("foo=bar");

        String out = template.requestBody("direct:start", "foo,bar", String.class);
        assertEquals("foo=bar", out);

        assertMockEndpointsSatisfied();
    }

    public void testBarFoo() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("bar");
        getMockEndpoint("mock:line").expectedBodiesReceived("bar", "foo");
        getMockEndpoint("mock:result").expectedBodiesReceived("bar=foo");

        String out = template.requestBody("direct:start", "bar,foo", String.class);
        assertEquals("bar=foo", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).continued(true).logContinued(false)
                        .to("log:error", "mock:error");

                from("direct:start")
                    .split(body(), new SplitAggregationStrategy()).shareUnitOfWork()
                        .to("mock:line")
                        .filter(simple("${body} == 'bar'"))
                            .throwException(new IllegalArgumentException("Forced"))
                        .end()
                    .end()
                    .to("log:result")
                    .to("mock:result");
            }
        };
    }

    private class SplitAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String s1 = oldExchange.getIn().getBody(String.class);
            String s2 = newExchange.getIn().getBody(String.class);
            String body = s1 + "=" + s2;
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }
    }

}