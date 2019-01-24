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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ToDynamicTest extends ContextTestSupport {

    @Test
    public void testToDynamic() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Hello Camel", "foo", "foo");
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testToDynamicInvalid() throws Exception {
        try {
            template.sendBody("direct:start", "Hello Camel");
            fail("Should fail");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ResolveEndpointFailedException.class, e.getCause());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .toD("mock:${header.foo}");
            }
        };
    }
}
