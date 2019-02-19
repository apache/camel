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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Test that longest context-path is preferred
 */
public class JettyLongestContextPathMatchTest extends BaseJettyTest {

    @Test
    public void testLongest() throws Exception {
        getMockEndpoint("mock:aaa").expectedMessageCount(1);
        getMockEndpoint("mock:bbb").expectedMessageCount(0);
        getMockEndpoint("mock:ccc").expectedMessageCount(0);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(1);
        getMockEndpoint("mock:bbb").expectedMessageCount(0);
        getMockEndpoint("mock:ccc").expectedMessageCount(0);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/ccc", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(0);
        getMockEndpoint("mock:bbb").expectedMessageCount(1);
        getMockEndpoint("mock:ccc").expectedMessageCount(0);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/bbb", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(0);
        getMockEndpoint("mock:bbb").expectedMessageCount(1);
        getMockEndpoint("mock:ccc").expectedMessageCount(0);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/bbb/foo", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(0);
        getMockEndpoint("mock:bbb").expectedMessageCount(0);
        getMockEndpoint("mock:ccc").expectedMessageCount(1);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/bbb/ccc/", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(0);
        getMockEndpoint("mock:bbb").expectedMessageCount(0);
        getMockEndpoint("mock:ccc").expectedMessageCount(1);
        getMockEndpoint("mock:ddd").expectedMessageCount(0);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/bbb/ccc/foo", null);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:aaa").expectedMessageCount(0);
        getMockEndpoint("mock:bbb").expectedMessageCount(0);
        getMockEndpoint("mock:ccc").expectedMessageCount(0);
        getMockEndpoint("mock:ddd").expectedMessageCount(1);
        template.sendBody("http://localhost:{{port}}/myapp/aaa/ddd/eee/fff/foo", null);
        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/aaa/?matchOnUriPrefix=true").to("mock:aaa");

                from("jetty:http://localhost:{{port}}/myapp/aaa/bbb/ccc/?matchOnUriPrefix=true").to("mock:ccc");

                from("jetty:http://localhost:{{port}}/myapp/aaa/ddd/eee/fff/?matchOnUriPrefix=true").to("mock:ddd");

                from("jetty:http://localhost:{{port}}/myapp/aaa/bbb/?matchOnUriPrefix=true").to("mock:bbb");

            }
        };
    }

}
