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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SedaInOutMEPTest extends ContextTestSupport {

    @Test
    public void testInOutMEP() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("InOut Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello InOut Camel");

        // InOut MEP when doing request
        Object out = template.requestBody("direct:start", "Camel");
        assertEquals("Hello InOut Camel", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        // force MEP back to InOnly as we want the next to define the MEP as InOut
                        .setExchangePattern(ExchangePattern.InOnly)
                        .to(ExchangePattern.InOut, "seda:foo")
                        .setBody(body().prepend("Hello "))
                        .to("mock:result");

                from("seda:foo").setBody(body().prepend("InOut ")).to("mock:foo");
            }
        };
    }
}
