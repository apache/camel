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
import org.junit.Test;

public class XPathFilterTest extends ContextTestSupport {
    protected String matchingBody = "<person name='James' city='London'/>";
    protected String notMatchingBody = "<person name='Hiram' city='Tampa'/>";

    @Test
    public void testSendMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(matchingBody);

        sendBody("direct:start", matchingBody);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        sendBody("direct:start", notMatchingBody);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").filter().xpath("/person[@name='James']").to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
