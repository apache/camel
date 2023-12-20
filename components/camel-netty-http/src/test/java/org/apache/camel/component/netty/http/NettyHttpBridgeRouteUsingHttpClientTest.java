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

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyHttpBridgeRouteUsingHttpClientTest extends BaseNettyTest {

    final AvailablePortFinder.Port port1 = port;
    @RegisterExtension
    AvailablePortFinder.Port port2 = AvailablePortFinder.find();

    @Test
    public void testBridge() {
        String response = template.requestBodyAndHeader("http://localhost:" + port2 + "/test/hello",
                new ByteArrayInputStream("This is a test".getBytes()), "Content-Type", "application/xml", String.class);
        assertEquals("/", response, "Get a wrong response");

        response = template.requestBody("http://localhost:" + port1 + "/hello/world", "hello", String.class);
        assertEquals("/hello/world", response, "Get a wrong response");

        assertThrows(RuntimeCamelException.class,
                () -> template.requestBody("http://localhost:" + port2 + "/hello/world", "hello", String.class));
    }

    @Test
    public void testSendFormRequestMessage() {
        String out = template.requestBodyAndHeader("http://localhost:" + port2 + "/form", "username=abc&pass=password",
                Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded", String.class);
        assertEquals("username=abc&pass=password", out, "Get a wrong response message");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());

                Processor serviceProc = exchange -> {
                    // get the request URL and copy it to the request body
                    String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                    exchange.getMessage().setBody(uri);
                };
                from("netty-http:http://localhost:" + port2 + "/test/hello")
                        .to("http://localhost:" + port1 + "?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("netty-http:http://localhost:" + port1 + "?matchOnUriPrefix=true").process(serviceProc);

                // check the from request
                from("netty-http:http://localhost:" + port2 + "/form?bridgeEndpoint=true")
                        .process(exchange -> {
                            // just take out the message body and send it back
                            Message in = exchange.getIn();
                            String request = in.getBody(String.class);
                            exchange.getMessage().setBody(request);
                        });
            }
        };
    }

}
