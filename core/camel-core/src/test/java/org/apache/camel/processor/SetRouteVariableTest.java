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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SetRouteVariableTest extends ContextTestSupport {
    private MockEndpoint end;
    private String variableName = "foo";
    private String expectedVariableValue = "bar";
    private String expectedVariableValue2 = "bar2";

    @Test
    public void testSetExchangeVariableMidRoute() throws Exception {
        assertNull(context.getVariable("route:myRoute:" + variableName));
        assertNull(context.getVariable("route:myRoute2:" + variableName));

        end.expectedMessageCount(2);

        template.sendBody("direct:start", "<blah/>");
        template.sendBody("direct:start2", "<blah/>");

        // make sure we got the message
        assertMockEndpointsSatisfied();

        // lets get the variable value
        List<Exchange> exchanges = end.getExchanges();
        Exchange exchange = exchanges.get(0);
        String actualVariableValue = exchange.getVariable("route:" + variableName, String.class);
        // should be stored on route so null
        assertNull(actualVariableValue);
        exchange = exchanges.get(1);
        actualVariableValue = exchange.getVariable("route:" + variableName, String.class);
        // should be stored on route so null
        assertNull(actualVariableValue);

        // should be stored as route variable
        assertEquals(expectedVariableValue, context.getVariable("route:myRoute:" + variableName));
        assertEquals(expectedVariableValue2, context.getVariable("route:myRoute2:" + variableName));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // stored as route variable
                from("direct:start").routeId("myRoute")
                        .setVariable("route:" + variableName).constant(expectedVariableValue).to("mock:end");

                from("direct:start2").routeId("myRoute2")
                        .setVariable("route:" + variableName).constant(expectedVariableValue2).to("mock:end");
            }
        };
    }
}
