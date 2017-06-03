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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyDefaultProtocolTest extends BaseNettyTest {

    @Test
    public void testDefaultProtocol() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:input").expectedHeaderReceived("beer", "yes");
        getMockEndpoint("mock:input").expectedHeaderReceived("host", "localhost:" + getPort());
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URL, "http://localhost:" + getPort() + "/foo");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_URI, "/foo");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_QUERY, "beer=yes");
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_PATH, "");

        String out = template.requestBody("netty-http:localhost:{{port}}/foo?beer=yes", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http:0.0.0.0:{{port}}/foo")
                    .to("mock:input")
                    .transform().constant("Bye World");
            }
        };
    }

}
