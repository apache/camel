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
package org.apache.camel.component.netty.http;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpSimpleBasicAuthTest extends BaseNettyTest {

    @Override
    public void setUp() throws Exception {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        System.clearProperty("java.security.auth.login.config");
        super.tearDown();
    }

    @Test
    public void testBasicAuth() throws Exception {
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class);
            fail("Should send back 401");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(401, cause.getStatusCode());
        }

        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        // username:password is scott:secret
        String auth = "Basic c2NvdHQ6c2VjcmV0";
        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", "Hello World", "Authorization", auth, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo?securityConfiguration.realm=karaf")
                    .to("mock:input")
                    .transform().constant("Bye World");
            }
        };
    }

}
