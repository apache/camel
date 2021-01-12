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
package org.apache.camel.itest.nettyhttp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NettyHttpClientChunkedResponseTest extends CamelTestSupport {

    private int port1;
    private int port2;

    @Disabled("TODO: investigate for Camel 3.0")
    @Test
    void testNettyHttpClientChunked() {
        invokeService(port1, true);
    }

    @Test
    void testNettyHttpRouteClientChunked() {
        invokeService(port2, false);
    }

    private void invokeService(int port, boolean checkChunkedHeader) {
        Exchange out = template.request("netty-http:http://localhost:" + port + "/test", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Camel in chunks.");
            }
        });

        assertNotNull(out);
        assertEquals("Bye Camel in chunks.", out.getMessage().getBody(String.class));
        if (checkChunkedHeader) {
            assertEquals("chunked", out.getMessage().getHeader("Transfer-Encoding"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                port1 = AvailablePortFinder.getNextAvailable();
                port2 = AvailablePortFinder.getNextAvailable();

                // use jetty as server as it supports sending response as chunked encoding
                from("jetty:http://localhost:" + port1 + "/test")
                        .setHeader("Transfer-Encoding", constant("chunked"))
                        .transform().simple("Bye ${body}");

                // set up a netty http proxy
                from("netty-http:http://localhost:" + port2 + "/test")
                        .to("netty-http:http://localhost:" + port1 + "/test?bridgeEndpoint=true&throwExceptionOnFailure=false");

            }
        };
    }
}
