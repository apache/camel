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

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyMDCLoggingTest extends BaseNettyTest {

    @Test
    public void testMDC() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("A", "B");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye A", "Bye B");

        String out = template.requestBody("direct:start", "A", String.class);
        assertEquals("Bye A", out);
        out = template.requestBody("direct:start", "B", String.class);
        assertEquals("Bye B", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable MDC
                context.setUseMDCLogging(true);

                from("direct:start").routeId("client")
                    .to("log:client-input")
                    .to("netty4-http:http://localhost:{{port}}/foo")
                    .to("log:client-output")
                    .to("mock:result");

                from("netty4-http:http://0.0.0.0:{{port}}/foo").routeId("server").streamCaching()
                    .to("log:server-input")
                    .to("mock:input")
                    .transform().simple("Bye ${body}")
                    .to("log:server-output");
            }
        };
    }

}
