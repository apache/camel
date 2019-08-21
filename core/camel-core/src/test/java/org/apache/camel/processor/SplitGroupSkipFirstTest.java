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

/**
 *
 */
public class SplitGroupSkipFirstTest extends ContextTestSupport {

    @Test
    public void testSplitSkipFirst() throws Exception {
        getMockEndpoint("mock:group").expectedBodiesReceived("ABC\nDEF\nGHI", "JKL\nMN");

        template.sendBody("direct:start", "##comment\nABC\nDEF\nGHI\nJKL\nMN");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitSkipFirstOnlyHeader() throws Exception {
        getMockEndpoint("mock:group").expectedBodiesReceived("");

        template.sendBody("direct:start", "##comment\n");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // split by new line and group by 3, and skip the very first
                    // element
                    .split().tokenize("\n", 3, true).streaming().to("mock:group");
                // END SNIPPET: e1
            }
        };
    }

}
