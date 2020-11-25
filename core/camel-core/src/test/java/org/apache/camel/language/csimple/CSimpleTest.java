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
package org.apache.camel.language.csimple;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class CSimpleTest extends ContextTestSupport {

    @Test
    public void testCSimple() throws Exception {
        getMockEndpoint("mock:high").expectedBodiesReceived("24", "20");
        getMockEndpoint("mock:med").expectedBodiesReceived("9", "6");
        getMockEndpoint("mock:low").expectedBodiesReceived("1", "2");

        template.sendBody("direct:start", 9);
        template.sendBody("direct:start", 1);
        template.sendBody("direct:start", 24);
        template.sendBody("direct:start", 2);
        template.sendBody("direct:start", 6);
        template.sendBody("direct:start", 20);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                        .when(csimple("${body} > 10")).to("mock:high")
                        .when(csimple("${body} > 5")).to("mock:med")
                        .otherwise().to("mock:low");
            }
        };
    }
}
