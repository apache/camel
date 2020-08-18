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

import java.net.URLEncoder;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpBridgeEncodedPathTest extends BaseNettyTest {

    private int port1;
    private int port2;
    private int port3;
    private int port4;

    @Test
    public void testEncodedQuery() throws Exception {
        String response = template.requestBody("http://localhost:" + port2 + "/nettyTestRouteA?param1=%2B447777111222", null,
                String.class);
        assertEquals("param1=+447777111222", response, "Get a wrong response");
    }

    @Test
    public void testEncodedPath() throws Exception {
        String path = URLEncoder.encode(" :/?#[]@!$", "UTF-8") + "/" + URLEncoder.encode("&'()+,;=", "UTF-8");
        MockEndpoint mock = getMockEndpoint("mock:encodedPath");
        mock.message(0).header(Exchange.HTTP_PATH).isEqualTo("/" + path);
        mock.message(0).header(Exchange.HTTP_QUERY).isNull();
        mock.message(0).header(Exchange.HTTP_RAW_QUERY).isNull();

        // cannot use template as it automatically decodes some chars in the path
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet("http://localhost:" + port4 + "/nettyTestRouteC/" + path);

            try (CloseableHttpResponse response = client.execute(httpGet)) {
                assertEquals(200, response.getStatusLine().getStatusCode(), "Get a wrong response status");
                assertMockEndpointsSatisfied();
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                port1 = getPort();
                port2 = getNextPort();
                port3 = getNextPort();
                port4 = getNextPort();

                errorHandler(noErrorHandler());

                Processor serviceProc = exchange -> {
                    // %2B becomes decoded to a space
                    Object s = exchange.getIn().getHeader("param1");
                    // can be either + or %2B
                    assertTrue(s.equals(" 447777111222") || s.equals("+447777111222") || s.equals("%2B447777111222"));

                    // send back the query
                    exchange.getMessage().setBody(exchange.getIn().getHeader(Exchange.HTTP_QUERY));
                };

                from("netty-http:http://localhost:" + port2 + "/nettyTestRouteA?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteA route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .to("netty-http:http://localhost:" + port1
                            + "/nettyTestRouteB?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("netty-http:http://localhost:" + port1 + "/nettyTestRouteB?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteB route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .process(serviceProc);

                from("netty-http:http://localhost:" + port4 + "/nettyTestRouteC?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteC route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .to("netty-http:http://localhost:" + port3
                            + "/nettyTestRouteD?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("netty-http:http://localhost:" + port3 + "/nettyTestRouteD?matchOnUriPrefix=true")
                        .log("Using NettyTestRouteD route: CamelHttpPath=[${header.CamelHttpPath}], CamelHttpUri=[${header.CamelHttpUri}]")
                        .setBody(constant("test"))
                        .to("mock:encodedPath");
            }
        };
    }

}
