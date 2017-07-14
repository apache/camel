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

public class ToDynamicLanguageSimpleAndXPathAndHeaderTest extends ContextTestSupport {

    public void testToDynamic() throws Exception {
        getMockEndpoint("mock:foo-123").expectedBodiesReceived("<order uri=\"foo\"/>");
        getMockEndpoint("mock:bar-456").expectedBodiesReceived("<order uri=\"bar\"/>");

        template.sendBodyAndHeader("direct:start", "<order uri=\"foo\"/>", "sub", "-123");
        template.sendBodyAndHeader("direct:start", "<order uri=\"bar\"/>", "sub", "-456");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .toD("mock:+language:xpath:/order/@uri+language:header:sub");
            }
        };
    }
}
