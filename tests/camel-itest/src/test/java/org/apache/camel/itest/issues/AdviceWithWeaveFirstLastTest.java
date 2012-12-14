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
package org.apache.camel.itest.issues;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
 
/**
 * Advice with tests
 */
public class AdviceWithWeaveFirstLastTest extends CamelTestSupport {
 
    @Override
    public boolean isUseAdviceWith() {
        return true;
    }
 
    @Test
    public void testWeaveAddFirst() throws Exception {
        // START SNIPPET: e1
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // insert at first the given piece of route to the existing route
                weaveAddFirst().to("mock:a").transform(constant("Bye World"));
            }
        });
        // END SNIPPET: e1
 
        context.start();
 
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
 
        template.sendBody("direct:start", "Hello World");
 
        assertMockEndpointsSatisfied();
    }
 
    @Test
    public void testWeaveAddLast() throws Exception {
        // START SNIPPET: e2
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // insert at the end of the existing route, the given piece of route
                weaveAddLast().to("mock:a").transform(constant("Bye World"));
            }
        });
        // END SNIPPET: e2
 
        context.start();
        
 
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
 
        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Bye World", out);
 
        assertMockEndpointsSatisfied();
    }
 
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e5
                from("direct:start")
                        .to("mock:foo")
                        .to("mock:bar").id("bar")
                        .to("mock:result");
                // END SNIPPET: e5
            }
        };
    }
}