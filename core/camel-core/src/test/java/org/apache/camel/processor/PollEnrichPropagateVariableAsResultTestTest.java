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
import org.junit.jupiter.api.Test;

public class PollEnrichPropagateVariableAsResultTestTest extends ContextTestSupport {

    @Test
    public void testPollEnrich() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCD");
        getMockEndpoint("mock:result").expectedVariableReceived("foo1", "AB");
        getMockEndpoint("mock:result").expectedVariableReceived("foo2", "ABC");
        getMockEndpoint("mock:result").expectedVariableReceived("foo3", "ABCD");

        template.sendBody("direct:slip1", "AB");
        template.sendBody("direct:slip2", "ABC");
        template.sendBody("direct:slip3", "ABCD");

        template.sendBody("direct:start", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich("seda:slip1", 1000)
                        .pollEnrich("seda:slip2", 1000)
                        .pollEnrich("seda:slip3", 1000)
                        .to("mock:result");

                from("direct:slip1")
                        .setVariable("foo1", simple("${body}"))
                        .to("seda:slip1");

                from("direct:slip2")
                        .setVariable("foo2", simple("${body}"))
                        .to("seda:slip2");

                from("direct:slip3")
                        .setVariable("foo3", simple("${body}"))
                        .to("seda:slip3");
            }
        };
    }
}
