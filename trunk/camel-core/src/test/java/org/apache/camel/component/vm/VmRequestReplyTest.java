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
package org.apache.camel.component.vm;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version 
 */
public class VmRequestReplyTest extends ContextTestSupport {

    public void testInOnly() throws Exception {
        // no problem for in only as we do not expect a reply
        template.sendBody("direct:start", "Hello World");
    }

    public void testInOut() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // create another camel context so we can use the vm component
        // to send messages between them
        CamelContext other = new DefaultCamelContext();
        other.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:foo").transform(constant("Bye World"));
            }
        });
        other.start();

        // should not fail even though its a request/reply but
        // we use the vm component so the consumer could be
        // in another camel context
        template.requestBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        other.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("vm:foo", "mock:result");
            }
        };
    }
}