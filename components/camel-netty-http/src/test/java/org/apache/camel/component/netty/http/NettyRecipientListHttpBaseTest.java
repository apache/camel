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

public class NettyRecipientListHttpBaseTest extends BaseNettyTest {

    @Test
    public void testRecipientListHttpBase() throws Exception {
        getMockEndpoint("mock:foo").expectedHeaderValuesReceivedInAnyOrder(Exchange.HTTP_PATH, "/bar", "/baz", "/bar/baz", "/baz/bar");
        getMockEndpoint("mock:foo").expectedHeaderValuesReceivedInAnyOrder("num", "1", "2", "3", "4");

        template.sendBodyAndHeader("direct:start", "A", Exchange.HTTP_PATH, "/foo/bar?num=1");
        template.sendBodyAndHeader("direct:start", "B", Exchange.HTTP_PATH, "/foo/baz?num=2");
        template.sendBodyAndHeader("direct:start", "C", Exchange.HTTP_PATH, "/foo/bar/baz?num=3");
        template.sendBodyAndHeader("direct:start", "D", Exchange.HTTP_PATH, "/foo/baz/bar?num=4");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/foo?matchOnUriPrefix=true")
                    .to("mock:foo")
                    .transform(body().prepend("Bye "));

                from("direct:start")
                    .recipientList().constant("netty-http:http://localhost:{{port}}");
            }
        };
    }

}
