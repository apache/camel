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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpProducerWithHeaderTest extends BaseNettyTestSupport {

    @Test
    public void testHttpSimpleGet() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");
        getMockEndpoint("mock:input").expectedHeaderReceived("myTraceId", "mockCorrelationID");

        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", null, "myTraceId",
                "mockCorrelationID", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHttpSimplePost() throws Exception {
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");
        getMockEndpoint("mock:input").expectedHeaderReceived("myTraceId", "mockCorrelationID");
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", "Hello World", "myTraceId",
                "mockCorrelationID", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .to("mock:input")
                        .transform().constant("Bye World");
            }
        };
    }

}
