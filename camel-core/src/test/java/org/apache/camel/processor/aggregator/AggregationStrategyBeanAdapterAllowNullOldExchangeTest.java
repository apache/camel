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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.junit.Test;

public class AggregationStrategyBeanAdapterAllowNullOldExchangeTest extends ContextTestSupport {

    private MyBodyAppender appender = new MyBodyAppender();
    private AggregationStrategyBeanAdapter myStrategy;

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("OldWasNullABC");

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myStrategy = new AggregationStrategyBeanAdapter(appender, "append");
                myStrategy.setAllowNullOldExchange(true);

                from("direct:start")
                    .aggregate(constant(true), myStrategy)
                        .completionSize(3)
                        .to("mock:result");
            }
        };
    }

    public static final class MyBodyAppender {

        public String append(String existing, String next) {
            if (existing == null) {
                return "OldWasNull" + next;
            }
            if (next != null) {
                return existing + next;
            } else {
                return existing;
            }
        }

    }
}
