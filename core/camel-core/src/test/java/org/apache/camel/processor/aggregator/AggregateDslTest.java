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

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class AggregateDslTest extends ContextTestSupport {

    @Test
    public void testAggregate() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("0,3", "1,4", "2,5");
        getMockEndpoint("mock:aggregated-supplier").expectedBodiesReceived("0,3,6", "1,4,7", "2,5,8");

        for (int i = 0; i < 9; i++) {
            template.sendBodyAndHeader("direct:start", i, "type", i % 3);
            template.sendBodyAndHeader("direct:start-supplier", i, "type", i % 3);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate().message(m -> m.getHeader("type")).strategy().body(String.class, AggregateDslTest::joinString).completion()
                    .body(String.class, s -> s.split(",").length == 2).to("mock:aggregated");

                from("direct:start-supplier").aggregate().header("type").strategy(AggregateDslTest::joinStringStrategy).completion()
                    .body(String.class, s -> s.split(",").length == 3).to("mock:aggregated-supplier");
            }
        };
    }

    // *************************************************************************
    // Strategies
    // *************************************************************************

    private static String joinString(String o, String n) {
        return Stream.of(o, n).filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private static Exchange joinStringStrategy(Exchange oldExchange, Exchange newExchange) {
        newExchange.getIn().setBody(joinString(oldExchange != null ? oldExchange.getIn().getBody(String.class) : null, newExchange.getIn().getBody(String.class)));

        return newExchange;
    }
}
