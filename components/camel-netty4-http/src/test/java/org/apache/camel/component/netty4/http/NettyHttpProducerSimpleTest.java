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

import io.netty.handler.codec.http.HttpResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

import org.junit.Test;

public class NettyHttpProducerSimpleTest extends BaseNettyTest {

    @Test
    public void testHttpSimple() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        String out = template.requestBody("netty4-http:http://localhost:{{port}}/foo", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpSimpleExchange() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        Exchange out = template.request("netty4-http:http://localhost:{{port}}/foo", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertNotNull(out);
        assertTrue(out.hasOut());

        NettyHttpMessage response = out.getOut(NettyHttpMessage.class);
        assertNotNull(response);
        assertEquals(200, response.getHttpResponse().status().code());

        // we can also get the response as body
        HttpResponse body = out.getOut().getBody(HttpResponse.class);
        assertNotNull(body);
        assertEquals(200, body.status().code());

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
