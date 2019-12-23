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
import org.junit.Test;

public class NettyHttpSameHostDifferentParametersTest extends BaseNettyTest {

    @Test
    public void testTwoRoutes() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(2);
        getMockEndpoint("mock:foo").message(0).header("param1").isEqualTo("value1");
        getMockEndpoint("mock:foo").message(1).header("param2").isEqualTo("value2");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo?param1=value1", "Hello World", String.class);
        assertEquals("param1=value1", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/foo?param2=value2", "Hello Camel", String.class);
        assertEquals("param2=value2", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo")
                    .to("mock:foo")
                    .transform().header(Exchange.HTTP_QUERY);
            }
        };
    }

}
