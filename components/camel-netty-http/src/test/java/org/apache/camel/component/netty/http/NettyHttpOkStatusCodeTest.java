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
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyHttpOkStatusCodeTest extends BaseNettyTestSupport {

    @Test
    public void testNoOk() {
        byte[] data = "Hello World".getBytes();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-200", data,
                        String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(209, cause.getStatusCode());
        String body = cause.getContentAsString();
        assertEquals("Not allowed", body);
    }

    @Test
    public void testNoOkSingleValue() {
        byte[] data = "Hello World".getBytes();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200", data,
                        String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(209, cause.getStatusCode());
        String body = cause.getContentAsString();
        assertEquals("Not allowed", body);
    }

    @Test
    public void testNoOkComplexRange() {
        byte[] data = "Hello World".getBytes();
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-204,301", data,
                        String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(209, cause.getStatusCode());
        String body = cause.getContentAsString();
        assertEquals("Not allowed", body);
    }

    @Test
    public void testOk() {
        byte[] data = "Hello World".getBytes();
        String out = template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-209", data,
                String.class);
        assertEquals("Not allowed", out);
    }

    @Test
    public void testOkComplexRange() {
        byte[] data = "Hello World".getBytes();
        String out = template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-204,209,301-304",
                data, String.class);
        assertEquals("Not allowed", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/test")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(209))
                        .transform().constant("Not allowed");
            }
        };
    }

}
