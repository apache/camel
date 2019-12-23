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
package org.apache.camel.component.netty.http;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpTwoRoutesMatchOnUriPrefixTest extends BaseNettyTest {

    @Test
    public void testTwoRoutesMatchOnUriPrefix() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Camel", "Hi Camel");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class);
        assertEquals("Bye World", out);

        // the foo is not match on prefix so we cannot do /foo/beer
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/foo/beer", "Hello World", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(404, cause.getStatusCode());
        }

        // .. and likewise baz is not a context-path we have mapped as input
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/baz", "Hello World", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(404, cause.getStatusCode());
        }

        out = template.requestBody("netty-http:http://localhost:{{port}}/bar", "Hello Camel", String.class);
        assertEquals("Bye Camel", out);

        // the bar is match on prefix so we can do /bar/beer
        out = template.requestBody("netty-http:http://localhost:{{port}}/bar/beer", "Hi Camel", String.class);
        assertEquals("Bye Camel", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:foo")
                    .transform().constant("Bye World");

                from("netty-http:http://0.0.0.0:{{port}}/bar?matchOnUriPrefix=true")
                    .to("mock:bar")
                    .transform().constant("Bye Camel");
            }
        };
    }

}
