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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Advice with tests
 */
public class AdviceWithWeaveToUriAndAddLastTest extends ContextTestSupport {

    @Test
    public void testWeaveByToUriAndAddLast() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToUri("mock:foo").replace().to("mock:foo2");
                // insert at the end of the existing route, the given piece of route
                weaveAddLast().to("mock:last");
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:foo2").expectedMessageCount(1);
        getMockEndpoint("mock:last").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "yeah");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:foo");
            }
        };
    }
}