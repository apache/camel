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
package org.apache.camel.component.ahc;

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AhcBridgeEndpointPathWithSpacesTest extends BaseAhcTest {

    private int port1;
    private int port2;

    @Test
    public void testBridgeEndpoint() throws Exception {
        String response = template.requestBodyAndHeader("http://localhost:" + port1 + "/test bar/hello",
                new ByteArrayInputStream("This is a test".getBytes()), "Content-Type", "application/xml", String.class);
        assertEquals("/proxy%20bar/hello", response, "Get a wrong response");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // get the request URL and copy it to the request body
                        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                        exchange.getMessage().setBody(uri);
                    }
                };

                from("jetty:http://localhost:" + port1 + "/test bar/?matchOnUriPrefix=true")
                        .to("ahc:http://localhost:" + port2 + "/proxy bar?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("jetty:http://localhost:" + port2 + "/proxy bar?matchOnUriPrefix=true")
                        .process(serviceProc);
            }
        };
    }
}
