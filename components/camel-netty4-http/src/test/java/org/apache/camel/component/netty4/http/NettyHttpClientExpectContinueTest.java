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
package org.apache.camel.component.netty4.http;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("TODO Fix it, need to send the response back")
public class NettyHttpClientExpectContinueTest extends BaseNettyTest {

    @Test
    public void testHttpExpect100Continue() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("request body");

        String body = "request body";
        DefaultExchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader("Expect", "100-continue");
        exchange.getIn().setBody(body);

        Exchange result = template.send("netty4-http:http://localhost:{{port}}/foo", exchange);
        
        assertFalse(result.isFailed());
        assertEquals("Bye World", result.getIn().getBody(String.class));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:input")
                    .transform().constant("Bye World");
            }
        };
    }

}
