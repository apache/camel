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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.junit.Test;

public class NettyHttpProducerKeepAliveTest extends BaseNettyTest {

    @Test
    public void testHttpKeepAlive() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World", "Hello Again");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=true", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=true", "Hello Again", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHttpKeepAliveFalse() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World", "Hello Again");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=false", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/foo?keepAlive=false", "Hello Again", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testConnectionClosed() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        Exchange ex = template.request("netty-http:http://localhost:{{port}}/bar?keepAlive=false", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
            
        });
        assertMockEndpointsSatisfied();
        assertEquals(HttpHeaders.Values.CLOSE, ex.getOut().getHeader(HttpHeaders.Names.CONNECTION));
    }
    

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/foo")
                    .to("mock:input")
                    .transform().constant("Bye World");
                
                from("netty-http:http://localhost:{{port}}/bar").removeHeaders("*").to("mock:input").transform().constant("Bye World");
            }
        };
    }

}
