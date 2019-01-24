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
import org.junit.Before;
import org.junit.Test;

public class RemoveHeaderTest extends ContextTestSupport {
    private MockEndpoint end;
    private MockEndpoint mid;
    private String headerName = "foo";
    private String expectedHeaderValue = "bar";

    @Test
    public void testSetHeaderMidRouteThenRemove() throws Exception {
        mid.expectedMessageCount(1);
        end.expectedMessageCount(1);
        
        template.sendBody("direct:start", "<blah/>");

        // make sure we got the message
        assertMockEndpointsSatisfied();

        List<Exchange> midExchanges = mid.getExchanges();
        Exchange midExchange = midExchanges.get(0);
        String actualHeaderValue = midExchange.getIn().getHeader(headerName, String.class);

        assertEquals(expectedHeaderValue, actualHeaderValue);
        
        List<Exchange> endExchanges = end.getExchanges();
        Exchange endExchange = endExchanges.get(0);
        
        // header should be removed
        assertNull(endExchange.getIn().getHeader(headerName, String.class));        
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        end = getMockEndpoint("mock:end");
        mid = getMockEndpoint("mock:mid");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                    setHeader(headerName).constant(expectedHeaderValue).to("mock:mid").
                    removeHeader(headerName).to("mock:end");
            }
        };
    }
}
