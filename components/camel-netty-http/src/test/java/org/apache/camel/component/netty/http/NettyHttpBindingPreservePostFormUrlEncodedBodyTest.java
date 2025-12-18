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

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpBindingPreservePostFormUrlEncodedBodyTest extends BaseNettyTestSupport {

    @Test
    public void testSendToNetty() {
        Exchange exchange
                = template.request("netty-http:http://localhost:{{port}}/myapp/myservice?query1=a&query2=b", exchange1 -> {
                    exchange1.getIn().setBody("b1=x&b2=y");
                    exchange1.getIn().setHeader("content-type", "application/x-www-form-urlencoded");
                    exchange1.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
                });
        // convert the response to a String
        String body = exchange.getMessage().getBody(String.class);
        assertEquals("Request message is OK", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/myapp/myservice").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);

                    // for unit testing make sure we got right message
                    assertEquals("b1=x&b2=y", body, "The body message is wrong");
                    assertEquals("a", exchange.getIn().getHeader("query1"),
                            "Get a wrong query parameter from the message header");
                    assertEquals("b", exchange.getIn().getHeader("query2"),
                            "Get a wrong query parameter from the message header");
                    assertEquals("x", exchange.getIn().getHeader("b1"), "Get a wrong form parameter from the message header");
                    assertEquals("y", exchange.getIn().getHeader("b2"), "Get a wrong form parameter from the message header");
                    assertEquals("localhost:" + getPort(), exchange.getIn().getHeader("host"),
                            "Get a wrong form parameter from the message header");

                    NettyHttpMessage in = (NettyHttpMessage) exchange.getIn();
                    FullHttpRequest request = in.getHttpRequest();
                    assertEquals("/myapp/myservice?query1=a&query2=b", request.uri(), "Relative path should be used");

                    // send a response
                    exchange.getMessage().getHeaders().clear();
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    exchange.getMessage().setBody("Request message is OK");
                });
            }
        };
    }

}
