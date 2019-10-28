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

import io.netty.channel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class NettyHttpPostDataTest extends BaseNettyTest {
    @Test
    public void testPostWWWFormUrlencoded() throws Exception {
        String body = "x=1&y=2";
        getMockEndpoint("mock:input").expectedBodiesReceived(body);

        DefaultExchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, NettyHttpConstants.CONTENT_TYPE_WWW_FORM_URLENCODED);
        exchange.getIn().setBody(body);

        Exchange result = template.send("netty-http:http://localhost:{{port}}/foo?reuseChannel=true", exchange);

        assertFalse(result.isFailed());
        assertEquals("expect the x is 1", "1", result.getIn().getHeader("x", String.class));
        assertEquals("expect the y is 2", "2", result.getIn().getHeader("y", String.class));
        assertMockEndpointsSatisfied();
        assertTrue(result.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class).isActive());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .to("mock:input");
            }
        };
    }
}
