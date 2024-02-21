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

public class OnCompletionBeforeChainedSedaRoutesTest extends ContextTestSupport {

    @Test
    public void testOnCompletionChained() throws Exception {
        final var body = "<body>";

        final var completionMockEndpoint = getMockEndpoint("mock:completion");

        completionMockEndpoint.expectedMessageCount(5);
        completionMockEndpoint.expectedBodiesReceived(
                "completion:a", "completion:b", "completion:c", body, "completion:d");

        template.sendBody("direct:a", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                    .onCompletion()
                            .modeBeforeConsumer()
                            .log("a - done")
                            .process(exchange -> exchange.getMessage().setBody("completion:a"))
                            .to("mock:completion")
                            .end()
                    .to("seda:b");

                from("seda:b")
                    .onCompletion()
                        .modeBeforeConsumer()
                        .log("b - done")
                        .process(exchange -> exchange.getMessage().setBody("completion:b"))
                        .to("mock:completion")
                        .end()
                    .delay(100)
                    .to("seda:c");

                from("seda:c")
                    .onCompletion()
                        .modeBeforeConsumer()
                        .log("c - done")
                        .process(exchange -> exchange.getMessage().setBody("completion:c"))
                        .to("mock:completion")
                        .end()
                    .delay(100)
                    .to("seda:d");

                from("seda:d")
                    .onCompletion()
                        .modeBeforeConsumer()
                        .log("d - done")
                        .process(exchange -> exchange.getMessage().setBody("completion:d"))
                        .to("mock:completion")
                        .end()
                    .delay(100)
                    .to("mock:completion");
            }
        };
    }
}
