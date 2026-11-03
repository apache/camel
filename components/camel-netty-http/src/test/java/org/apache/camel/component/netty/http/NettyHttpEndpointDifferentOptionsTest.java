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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyHttpEndpointDifferentOptionsTest extends BaseNettyTestSupport {

    @Test
    public void testEndpointsWithDifferentOptionsAreNotEqual() {
        Endpoint plain = context.getEndpoint("netty-http:http://localhost:{{port}}/foo?bar=123");
        Endpoint option = context.getEndpoint("netty-http:http://localhost:{{port}}/foo?bar=123&throwExceptionOnFailure=false");

        assertNotEquals(plain, option);
        assertNotEquals(plain.getEndpointUri(), option.getEndpointUri());

        // both send to the same address, which keeps the query parameters that are not endpoint options
        String url = "http://localhost:" + getPort() + "/foo?bar=123";
        assertEquals(url, NettyHttpHelper.createURL(new DefaultExchange(context), (NettyHttpEndpoint) plain));
        assertEquals(url, NettyHttpHelper.createURL(new DefaultExchange(context), (NettyHttpEndpoint) option));
    }

    @Test
    public void testProducerTemplateUsesTheOptionsOfEachEndpoint() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/fail", "Hello World", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(500, cause.getStatusCode());

        // the same address with another option is another endpoint, with a producer of its own
        String out = template.requestBody("netty-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=false",
                "Hello World", String.class);
        assertEquals("Failed", out);
    }

    @Test
    public void testToDUsesTheOptionsOfEachEndpoint() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:start", "Hello World", "throwOnFailure", "true", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(500, cause.getStatusCode());

        String out = template.requestBodyAndHeader("direct:start", "Hello World", "throwOnFailure", "false", String.class);
        assertEquals("Failed", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/fail")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                        .transform().constant("Failed");

                from("direct:start")
                        .toD("netty-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=${header.throwOnFailure}");
            }
        };
    }
}
