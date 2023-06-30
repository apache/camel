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

import java.nio.charset.StandardCharsets;

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpAccessHttpRequestBeanTest extends BaseNettyTest {

    @Test
    public void testAccessHttpRequest() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo", "World", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .to("mock:input")
                        .transform().method(NettyHttpAccessHttpRequestBeanTest.class, "myTransformer");
            }
        };
    }

    public static String myTransformer(FullHttpRequest request) {
        String in = request.content().toString(StandardCharsets.UTF_8);
        // release as no longer in use
        request.content().release();
        return "Bye " + in;
    }

}
