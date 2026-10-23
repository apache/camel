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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class NettyHttpDifferentHostSamePortTest extends BaseNettyTestSupport {

    @Test
    public void testDifferentHostSamePortHandlersAreDistinct() {
        NettyHttpComponent component = context.getComponent("netty-http", NettyHttpComponent.class);
        int port = getPort();
        HttpServerConsumerChannelFactory handler1 = component.getMultiplexChannelHandler("127.0.0.1:" + port, port);
        HttpServerConsumerChannelFactory handler2 = component.getMultiplexChannelHandler("127.0.0.2:" + port, port);
        assertNotSame(handler1, handler2);
    }

    @Test
    public void testRoutesOnDifferentHostsSamePortDoNotCrossDispatch() {
        int port = getPort();

        String fooResponse = template.requestBody("netty-http:http://127.0.0.1:" + port + "/foo", "request", String.class);
        assertEquals("foo", fooResponse);

        String barResponse = template.requestBody("netty-http:http://127.0.0.2:" + port + "/bar", "request", String.class);
        assertEquals("bar", barResponse);

        Exchange out1 = template.request("netty-http:http://127.0.0.1:" + port + "/bar?throwExceptionOnFailure=false",
                exchange -> exchange.getIn().setBody("request"));
        assertEquals(404, out1.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        Exchange out2 = template.request("netty-http:http://127.0.0.2:" + port + "/foo?throwExceptionOnFailure=false",
                exchange -> exchange.getIn().setBody("request"));
        assertEquals(404, out2.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://127.0.0.1:{{port}}/foo")
                        .transform().constant("foo");

                from("netty-http:http://127.0.0.2:{{port}}/bar")
                        .transform().constant("bar");
            }
        };
    }
}
