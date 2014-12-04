/**
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

public class RemovePropertiesWithExclusionTest extends ContextTestSupport {
    private MockEndpoint end;
    private MockEndpoint mid;
    private String propertyName = "foo";
    private String expectedPropertyValue = "bar";
    private String propertyName1 = "fee";
    private String expectedPropertyValue1 = "bar1";
    private String propertyName2 = "fiu";
    private String expectedPropertyValue2 = "bar2";
    private String pattern = "f*";
    private String exclusion = "fiu";

    public void testSetExchangePropertiesMidRouteThenRemoveWithPatternAndExclusion() throws Exception {
        mid.expectedMessageCount(1);
        end.expectedMessageCount(1);
        
        template.sendBody("direct:start", "message");

        // make sure we got the message
        assertMockEndpointsSatisfied();

        List<Exchange> midExchanges = mid.getExchanges();
        Exchange midExchange = midExchanges.get(0);
        String actualPropertyValue = midExchange.getProperty(propertyName, String.class);
        String actualPropertyValue1 = midExchange.getProperty(propertyName1, String.class);
        String actualPropertyValue2 = midExchange.getProperty(propertyName2, String.class);

        assertEquals(expectedPropertyValue, actualPropertyValue);
        assertEquals(expectedPropertyValue1, actualPropertyValue1);
        assertEquals(expectedPropertyValue2, actualPropertyValue2);
        
        List<Exchange> endExchanges = end.getExchanges();
        Exchange endExchange = endExchanges.get(0);
        
        // properties should be removed but the last still have to be in the exchange
        assertNull(endExchange.getProperty(propertyName, String.class));
        assertNull(endExchange.getProperty(propertyName1, String.class));
        assertEquals(expectedPropertyValue2, endExchange.getProperty(propertyName2, String.class));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
        mid = getMockEndpoint("mock:mid");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setProperty(propertyName).constant(expectedPropertyValue)
                    .setProperty(propertyName1).constant(expectedPropertyValue1)
                    .setProperty(propertyName2).constant(expectedPropertyValue2).to("mock:mid").
                    removeProperties(pattern, exclusion).to("mock:end");
            }
        };
    }
}
