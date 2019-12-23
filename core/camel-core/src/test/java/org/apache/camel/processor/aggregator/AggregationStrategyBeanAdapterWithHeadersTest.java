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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AggregationStrategyBeanAdapterWithHeadersTest extends ContextTestSupport {

    private MyBodyAppender appender = new MyBodyAppender();

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABC");
        getMockEndpoint("mock:result").expectedHeaderReceived("count", 6);

        template.sendBodyAndHeader("direct:start", "A", "count", 1);
        template.sendBodyAndHeader("direct:start", "B", "count", 2);
        template.sendBodyAndHeader("direct:start", "C", "count", 3);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(constant(true), AggregationStrategies.bean(appender, "appendWithHeaders")).completionSize(3).to("mock:result");
            }
        };
    }

    public static final class MyBodyAppender {

        public String appendWithHeaders(String existing, Map<String, Integer> oldHeaders, String next, Map<String, Integer> newHeaders) {
            if (next != null) {
                Integer count = oldHeaders.get("count") + newHeaders.get("count");
                oldHeaders.put("count", count);
                return existing + next;
            } else {
                return existing;
            }
        }
    }
}
