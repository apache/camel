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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SplitterWithDualMulticastTest extends ContextTestSupport {

    @Test
    public void testSplitterWithDualMulticast() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C");
        // should get the original input message without any headers etc
        getMockEndpoint("mock:result").allMessages().header("bar").isNull();
        getMockEndpoint("mock:result").allMessages().header("foo").isNull();
        getMockEndpoint("mock:result").allMessages().header("beer").isNull();

        getMockEndpoint("mock:split").expectedBodiesReceived("A", "B", "C");
        // should have the bar header because multicast uses
        // UseLatestAggregationStrategy by default
        getMockEndpoint("mock:split").expectedHeaderReceived("bar", 123);
        // should NOT have the foo header because multicast uses
        // UseLatestAggregationStrategy by default
        getMockEndpoint("mock:split").allMessages().header("foo").isNull();

        getMockEndpoint("mock:split2").expectedBodiesReceived("A", "B", "C");
        // should have the bar header from the previous multicast (which value
        // 123)
        getMockEndpoint("mock:split2").expectedHeaderReceived("bar", 123);
        // should have the beer which was set on the 2nd multicast
        getMockEndpoint("mock:split2").expectedHeaderReceived("beer", "Carlsberg");
        // and no foo header at all
        getMockEndpoint("mock:split2").allMessages().header("foo").isNull();

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body().tokenize(",")).multicast().setHeader("foo", constant("ABC")).setHeader("bar", constant(123)).end()
                    .to("log:split?showHeaders=true", "mock:split").multicast().setHeader("bar", constant(456)).setHeader("beer", constant("Carlsberg")).end()
                    .to("log:split2?showHeaders=true", "mock:split2").end().to("log:result?showHeaders=true", "mock:result");
            }
        };
    }
}
