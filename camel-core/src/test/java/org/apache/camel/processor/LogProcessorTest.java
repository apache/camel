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
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class LogProcessorTest extends ContextTestSupport {

    public void testLogProcessorFoo() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testLogProcessorBar() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:bar", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testLogProcessorBaz() throws Exception {
        getMockEndpoint("mock:baz").expectedMessageCount(1);

        template.sendBody("direct:baz", "Hi World");

        assertMockEndpointsSatisfied();
    }

    public void testLogProcessorMarker() throws Exception {
        getMockEndpoint("mock:wombat").expectedMessageCount(1);

        template.sendBody("direct:wombat", "Hi Wombat");

        assertMockEndpointsSatisfied();
    }

    public void testNoLog() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:nolog", "Hi World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").log("Got ${body}").to("mock:foo");

                from("direct:bar").routeId("bar").log(LoggingLevel.WARN, "Also got ${body}").to("mock:bar");

                from("direct:baz").routeId("baz").log(LoggingLevel.ERROR, "cool", "Me got ${body}").to("mock:baz");

                from("direct:wombat").routeId("wombat")
                    .log(LoggingLevel.INFO, "cool", "mymarker", "Me got ${body}")
                    .to("mock:wombat");

                from("direct:nolog").routeId("nolog").log(LoggingLevel.TRACE, "Should not log ${body}").to("mock:bar");
            }
        };
    }

}
