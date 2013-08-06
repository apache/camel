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
package org.apache.camel.itest.nettyhttp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class NettyHttpClientChunkedResponseTest extends CamelTestSupport {

    private int port;

    @Test
    public void testNettyHttpClientChunked() throws Exception {
        Exchange out = template.request("netty-http:http://localhost:" + port + "/test", new Processor() {
//        Exchange out = template.request("jetty:http://localhost:" + port + "/test", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Camel in chunks.");
            }
        });

        assertNotNull(out);

        assertEquals("Bye Camel in chunks.", out.getOut().getBody(String.class));
        assertEquals("chunked", out.getOut().getHeader("Transfer-Encoding"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port = AvailablePortFinder.getNextAvailable(8000);

                // use jetty as server as it supports sending response as chunked encoding
                from("jetty:http://localhost:" + port + "/test")
                    .setHeader("Transfer-Encoding", constant("chunked"))
                    .transform().simple("Bye ${body}");
            }
        };
    }
}
