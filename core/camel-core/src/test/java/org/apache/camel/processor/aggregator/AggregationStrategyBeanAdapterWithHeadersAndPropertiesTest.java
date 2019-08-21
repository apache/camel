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

public class AggregationStrategyBeanAdapterWithHeadersAndPropertiesTest extends ContextTestSupport {

    private MyBodyAppender appender = new MyBodyAppender();

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABC");
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "yesyesyes");
        getMockEndpoint("mock:result").expectedPropertyReceived("count", 6);

        template.sendBodyAndProperty("direct:start", "A", "count", 1);
        template.sendBodyAndProperty("direct:start", "B", "count", 2);
        template.sendBodyAndProperty("direct:start", "C", "count", 3);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setHeader("foo", constant("yes")).aggregate(constant(true), AggregationStrategies.bean(appender, "appendWithHeadersAndProperties"))
                    .completionSize(3).to("mock:result");
            }
        };
    }

    public static final class MyBodyAppender {

        public String appendWithHeadersAndProperties(String existing, Map<String, String> oldHeaders, Map<String, Integer> oldProperties, String next,
                                                     Map<String, String> newHeaders, Map<String, Integer> newProperties) {
            if (next != null) {
                Integer count = oldProperties.get("count") + newProperties.get("count");
                oldProperties.put("count", count);
                String foo = oldHeaders.get("foo") + newHeaders.get("foo");
                oldHeaders.put("foo", foo);
                return existing + next;
            } else {
                return existing;
            }
        }
    }
}
