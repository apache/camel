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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpOkStatusCodeTest extends BaseNettyTest {

    @Test
    public void testNoOk() throws Exception {
        byte[] data = "Hello World".getBytes();
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-200", data, String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(209, cause.getStatusCode());
            String body = context.getTypeConverter().convertTo(String.class, cause.getResponse().getContent());
            assertEquals("Not allowed", body);
        }
    }

    @Test
    public void testNoOkComplexRange() throws Exception {
        byte[] data = "Hello World".getBytes();
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-204,301", data, String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(209, cause.getStatusCode());
            String body = context.getTypeConverter().convertTo(String.class, cause.getResponse().getContent());
            assertEquals("Not allowed", body);
        }
    }

    @Test
    public void testOk() throws Exception {
        byte[] data = "Hello World".getBytes();
        String out = template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-204,209,301-304", data, String.class);
        assertEquals("Not allowed", out);
    }
    @Test
    public void testOkComplexRange() throws Exception {
        byte[] data = "Hello World".getBytes();
        String out = template.requestBody("netty-http:http://localhost:{{port}}/test?okStatusCodeRange=200-204,209,301-304", data, String.class);
        assertEquals("Not allowed", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/test")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(209))
                    .transform().constant("Not allowed");
            }
        };
    }

}
