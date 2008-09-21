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
package org.apache.camel.builder.script;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Tests a routing expression using JavaScript
 */
public class JavaScriptExpressionTest extends ContextTestSupport {
    
    public void testSendMatchingMessage() throws Exception {
        // TODO Currently, this test fails because the JavaScript expression in createRouteBuilder
        // below returns false
        // To fix that, we need to figure out how to get the expression to return the right value
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        getMockEndpoint("mock:unmatched").expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        sendBody("direct:start", "hello", headers);

        assertEquals("Should get the message header here", mock.getExchanges().get(0).getIn().getHeader("foo"), "bar");
        assertMockEndpointsSatisfied();
    }

    public void testSendNonMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:unmatched").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "foo");
        sendBody("direct:start", "hello", headers);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").choice().
                        when().javaScript("request.headers.get('foo') == 'bar'").to("log:info?showAll=true").to("mock:result")
                        .otherwise().to("mock:unmatched");
            }
        };
    }
}