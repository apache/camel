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
package org.apache.camel.component.direct;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class SendingToAlotOfDifferentDirectEndpointTest extends ContextTestSupport {

    public void testDirect() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(3);

        template.sendBody("seda:start", "Hello World");

        // now create 1000 other endpoints to cause the first direct endpoint to vanish from the LRUCache
        for (int i = 0; i < 1200; i++) {
            context.getEndpoint("direct:bar-" + i);
        }

        template.sendBody("direct:foo", "Bye Moon");
        template.sendBody("seda:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").to("direct:foo");

                from("direct:foo").to("log:foo").to("mock:foo");
            }
        };
    }
}
