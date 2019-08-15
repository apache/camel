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

import java.net.URL;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.apache.camel.Exchange.HTTP_QUERY;
import static org.apache.camel.Exchange.HTTP_RAW_QUERY;

public class NettyHttpRawQueryTest extends BaseNettyTest {

    @EndpointInject("mock:test")
    MockEndpoint mockEndpoint;

    @Test
    public void shouldAccessRawQuery() throws Exception {
        String query = "param=x1%26y%3D2";
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.message(0).header(HTTP_QUERY).isEqualTo("param=x1&y=2");
        mockEndpoint.message(0).header(HTTP_RAW_QUERY).isEqualTo(query);

        new URL("http://localhost:" + getPort() + "/?" + query).openConnection().getInputStream().close();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/")
                    .to(mockEndpoint);
            }
        };
    }

}
