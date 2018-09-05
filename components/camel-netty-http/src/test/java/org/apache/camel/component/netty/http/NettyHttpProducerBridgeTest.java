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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NettyHttpProducerBridgeTest extends BaseNettyTest {

    private int port1;
    private int port2;
    private int port3;

    @Test
    public void testProxy() throws Exception {
        String reply = template.requestBody("netty-http:http://localhost:" + port1 + "/foo", "World", String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testBridgeWithQuery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.message(0).header(Exchange.HTTP_RAW_QUERY).isEqualTo("x=%3B");
        mock.message(0).header(Exchange.HTTP_QUERY).isEqualTo("x=;");

        template.request("netty-http:http://localhost:" + port3 + "/query?bridgeEndpoint=true", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
                exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=%3B");
            }
        });
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBridgeWithRawQueryAndQuery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.message(0).header(Exchange.HTTP_RAW_QUERY).isEqualTo("x=%3B");
        mock.message(0).header(Exchange.HTTP_QUERY).isEqualTo("x=;");

        template.request("netty-http:http://localhost:" + port3 + "/query?bridgeEndpoint=true", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_URI, "http://host:8080/");
                exchange.getIn().setHeader(Exchange.HTTP_RAW_QUERY, "x=%3B");
                exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=;");
            }
        });
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();
                port3 = getNextPort();

                from("netty-http:http://0.0.0.0:" + port1 + "/foo")
                        .to("netty-http:http://localhost:" + port2 + "/bar?bridgeEndpoint=true&throwExceptionOnFailure=false");

                from("netty-http:http://0.0.0.0:" + port2 + "/bar")
                        .transform().simple("Bye ${body}");

                from("netty-http:http://0.0.0.0:" + port3 + "/query")
                        .to("mock:query");
            }
        };
    }

}
