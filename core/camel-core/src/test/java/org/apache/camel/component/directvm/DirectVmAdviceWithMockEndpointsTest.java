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
package org.apache.camel.component.directvm;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("DirectVmComponent")
public class DirectVmAdviceWithMockEndpointsTest extends AbstractDirectVmTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testMockEndpoints() throws Exception {
        context.addRoutes(createRouteBuilder());

        // advice
        AdviceWith.adviceWith(context.getRouteDefinition("quotes"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints();
            }
        });

        // must start Camel after we are done using advice-with
        context.start();

        getMockEndpoint("mock:seda:camel").expectedBodiesReceived("Camel rocks");
        getMockEndpoint("mock:seda:other").expectedBodiesReceived("Bad donkey");

        template.sendBody("direct-vm:quotes", "Camel rocks");
        template.sendBody("direct-vm:quotes", "Bad donkey");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:quotes").routeId("quotes")
                        .choice()
                        .when(simple("${body} contains 'Camel'"))
                        .to("seda:camel")
                        .otherwise()
                        .to("seda:other");
            }
        };
    }
}
