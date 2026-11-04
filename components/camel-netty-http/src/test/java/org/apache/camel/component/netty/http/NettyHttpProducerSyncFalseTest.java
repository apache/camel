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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * With sync=false the producer does not wait for the response, and the response that arrives later is not set on the
 * exchange.
 */
public class NettyHttpProducerSyncFalseTest extends BaseNettyTestSupport {

    @Test
    public void testResponseIgnored() throws Exception {
        MockEndpoint server = getMockEndpoint("mock:server");
        server.expectedBodiesReceived("Hello World");

        Exchange exchange = template.request("netty-http:http://localhost:{{port}}/foo?sync=false",
                e -> e.getIn().setBody("Hello World"));

        MockEndpoint.assertIsSatisfied(context);
        // the response the server sends back must not replace the message of the exchange that already completed
        await().during(500, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> "Hello World".equals(exchange.getMessage().getBody(String.class)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                        .to("mock:server")
                        .setBody().constant("Bye World");
            }
        };
    }
}
