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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class SimpleResultTypeRouteTest extends ContextTestSupport {

    public void testSimpleResultTypeFoo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.expectedMessageCount(1);
        // cool header is a boolean
        mock.message(0).header("cool").isInstanceOf(Boolean.class);
        mock.message(0).header("cool").isEqualTo(true);
        // fail header is not a boolean
        mock.message(0).header("fail").isInstanceOf(String.class);
        mock.message(0).header("fail").isEqualTo("true");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testSimpleResultTypeBar() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedMessageCount(1);
        // cool header is a boolean
        mock.message(0).header("cool").isInstanceOf(Boolean.class);
        mock.message(0).header("cool").isEqualTo(true);
        // fail header is not a boolean
        mock.message(0).header("fail").isInstanceOf(String.class);
        mock.message(0).header("fail").isEqualTo("true");

        template.sendBody("direct:bar", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo")
                    // set using builder support
                    .setHeader("cool", simple("true", Boolean.class))
                    .setHeader("fail", simple("true"))
                    .to("mock:foo");

                from("direct:bar")
                    // set using expression clause
                    .setHeader("cool").simple("true", Boolean.class)
                    .setHeader("fail", simple("true"))
                    .to("mock:bar");
            }
        };
    }
}
