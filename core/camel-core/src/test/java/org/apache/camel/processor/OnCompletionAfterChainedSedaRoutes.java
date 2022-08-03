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

public class OnCompletionAfterChainedSedaRoutes extends ContextTestSupport {

    @Test
    public void testOnCompletionChained() throws Exception {
        String body = "<body>";

        getMockEndpoint("mock:end").expectedBodiesReceived(body);
        getMockEndpoint("mock:adone").expectedBodiesReceived(body);
        getMockEndpoint("mock:bdone").expectedBodiesReceived(body);
        getMockEndpoint("mock:cdone").expectedBodiesReceived(body);
        getMockEndpoint("mock:ddone").expectedBodiesReceived(body);

        template.sendBody("direct:a", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                .onCompletion().log("a - done").to("mock:adone").end()
                .to("seda:b");

                from("seda:b")
                .onCompletion().log("b - done").to("mock:bdone").end()
                .to("seda:c");

                from("seda:c")
                .onCompletion().log("c - done").to("mock:cdone").end()
                .to("seda:d");

                from("seda:d")
                .onCompletion().log("d - done").to("mock:ddone").end()
                .to("mock:end");
            }

        };
    }
}
