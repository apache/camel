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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DynamicRouter2Test extends ContextTestSupport {

    public void testDynamicRouter() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // use a bean as the dynamic router
                    .dynamicRouter().method(DynamicRouter2Test.class, "slip");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    /**
     * Use this method to compute dynamic where we should route next.
     *
     * @param body the message body
     * @param previous the previous slip
     * @return endpoints to go, or <tt>null</tt> to indicate the end
     */
    public String slip(String body, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        if (previous == null) {
            return "mock:a";
        } else if ("mock://a".equals(previous)) {
            return "mock:b";
        } else if ("mock://b".equals(previous)) {
            return "mock:result";
        }

        // no more so return null
        return null;
    }
    // END SNIPPET: e2

}
