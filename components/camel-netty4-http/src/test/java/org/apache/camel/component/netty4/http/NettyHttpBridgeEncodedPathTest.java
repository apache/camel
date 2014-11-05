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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpBridgeEncodedPathTest extends BaseNettyTest {

    private int port1;
    private int port2;

    @Test
    public void testHttpClient() throws Exception {
        String response = template.requestBody("http://localhost:" + port2 + "/nettyTestRouteA?param1=%2B447777111222", null, String.class);
        assertEquals("Get a wrong response", "param1=+447777111222", response);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                port1 = getPort();
                port2 = getNextPort();

                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // %2B becomes decoded to a space
                        Object s = exchange.getIn().getHeader("param1");
                        // can be either + or %2B
                        assertTrue(s.equals(" 447777111222") || s.equals("+447777111222") || s.equals("%2B447777111222"));

                        // send back the query
                        exchange.getOut().setBody(exchange.getIn().getHeader(Exchange.HTTP_QUERY));
                    }
                };
                from("netty4-http://http://localhost:" + port2 + "/nettyTestRouteA?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteA route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .to("netty4-http://http://localhost:" + port1 + "/nettyTestRouteB?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("netty4-http://http://localhost:" + port1 + "/nettyTestRouteB?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteB route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .process(serviceProc);
            }
        };
    }

}
