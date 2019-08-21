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
package org.apache.camel.issues;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RecipientListAggregationStrategyInputExchangeTest extends ContextTestSupport {

    @Test
    public void testInputExchange() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);

        Exchange out = template.request("direct:start", p -> p.getMessage().setBody("Hello World"));
        assertNotNull(out);
        assertEquals("Hello World", out.getMessage().getBody());
        assertEquals("Forced", out.getMessage().getHeader("FailedDue"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").recipientList(constant("direct:a,direct:b")).aggregationStrategy(new MyAggregateBean());

                from("direct:a").setHeader("foo", constant("123")).transform(constant("A")).to("mock:a");
                from("direct:b").setHeader("bar", constant("456")).transform(constant("B")).throwException(new IllegalArgumentException("Forced")).to("mock:b");
            }
        };
    }

    public static class MyAggregateBean implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // NOT in use
            return null;
        }

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange, Exchange inputExchange) {
            if (newExchange.isFailed()) {
                inputExchange.getMessage().setHeader("FailedDue", newExchange.getException().getMessage());
                return inputExchange;
            }
            // dont care so much about merging in this unit test
            return newExchange;
        }
    }

}
