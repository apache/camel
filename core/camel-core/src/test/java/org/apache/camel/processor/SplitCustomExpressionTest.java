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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class SplitCustomExpressionTest extends ContextTestSupport {

    @Test
    public void testSplitCustomExpression() throws Exception {
        getMockEndpoint("mock:split").expectedBodiesReceived("A", "B", "C");

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(new MyCustomExpression()).to("mock:split");
            }
        };
    }

    public static class MyCustomExpression implements Expression {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            final String body = exchange.getIn().getBody(String.class);

            // just split the body by comma
            String[] parts = body.split(",");
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                list.add(part);
            }

            return (T)list.iterator();
        }
    }
}
