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

public class RemoveVariablesWithExclusionTest extends ContextTestSupport {
    private MockEndpoint end;
    private MockEndpoint mid;
    private String variableName = "foo";
    private String expectedVariableValue = "bar";
    private String variableName1 = "fee";
    private String expectedVariableValue1 = "bar1";
    private String variableName2 = "fiu";
    private String expectedVariableValue2 = "bar2";
    private String pattern = "f*";
    private String exclusion = "fiu";

    @Test
    public void testSetVariablesMidRouteThenRemoveWithPatternAndExclusion() throws Exception {
        mid.expectedMessageCount(1);
        end.expectedMessageCount(1);

        template.sendBody("direct:start", "message");

        // make sure we got the message
        assertMockEndpointsSatisfied();

        List<Exchange> midExchanges = mid.getExchanges();
        Exchange midExchange = midExchanges.get(0);
        String actualPropertyValue = midExchange.getVariable(variableName, String.class);
        String actualPropertyValue1 = midExchange.getVariable(variableName1, String.class);
        String actualPropertyValue2 = midExchange.getVariable(variableName2, String.class);

        assertEquals(expectedVariableValue, actualPropertyValue);
        assertEquals(expectedVariableValue1, actualPropertyValue1);
        assertEquals(expectedVariableValue2, actualPropertyValue2);

        List<Exchange> endExchanges = end.getExchanges();
        Exchange endExchange = endExchanges.get(0);

        // variables should be removed but the last still have to be in the
        // exchange
        assertNull(endExchange.getVariable(variableName, String.class));
        assertNull(endExchange.getVariable(variableName1, String.class));
        assertEquals(expectedVariableValue2, endExchange.getVariable(variableName2, String.class));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
        mid = getMockEndpoint("mock:mid");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setVariable(variableName).constant(expectedVariableValue).setVariable(variableName1)
                        .constant(expectedVariableValue1)
                        .setVariable(variableName2).constant(expectedVariableValue2).to("mock:mid")
                        .removeVariables(pattern, exclusion).to("mock:end");
            }
        };
    }
}
