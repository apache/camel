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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Testing http://camel.apache.org/dsl.html
 */
public class InterceptFromUriSimpleLogTest extends ContextTestSupport {

    public void testInterceptLog() throws Exception {
        getMockEndpoint("mock:first").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("seda:foo", "Bye World");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // only trigger when incoming from seda:bar endpoint
                interceptFrom("seda:bar").to("mock:bar");

                // and here we have a couple of routes
                from("direct:start").to("mock:first").to("seda:bar");

                from("seda:bar").to("mock:result");

                from("seda:foo").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}