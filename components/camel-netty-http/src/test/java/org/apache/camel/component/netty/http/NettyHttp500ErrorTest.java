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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttp500ErrorTest extends BaseNettyTest {

    @Test
    public void testHttp500Error() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        try {
            template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class);
            fail("Should have failed");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(500, cause.getStatusCode());
            assertEquals("Camel cannot do this", cause.getContentAsString());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttp500ErrorDisabled() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        String body = template.requestBody("netty-http:http://localhost:{{port}}/foo?throwExceptionOnFailure=false", "Hello World", String.class);
        assertEquals("Camel cannot do this", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttp500ErrorDisabledStatusCode() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        Exchange out = template.request("netty-http:http://localhost:{{port}}/foo?throwExceptionOnFailure=false", exchange -> exchange.getIn().setBody("Hello World"));
        assertNotNull(out);

        assertEquals(500, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Internal Server Error", out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:input")
                    // trigger failure by setting error code to 500
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setBody().constant("Camel cannot do this");
            }
        };
    }

}
