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
package org.apache.camel.component.netty4;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class NettyProxyTest extends BaseNettyTest {

    private int port1;
    private int port2;

    @Test
    public void testNettyProxy() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:proxy").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");

        Object body = template.requestBody("netty4:tcp://localhost:" + port1 + "?sync=true&textline=true", "Camel\n");
        assertEquals("Bye Camel", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                fromF("netty4:tcp://localhost:%s?sync=true&textline=true", port1)
                    .to("mock:before")
                    .toF("netty4:tcp://localhost:%s?sync=true&textline=true", port2)
                    .to("mock:after");

                fromF("netty4:tcp://localhost:%s?sync=true&textline=true", port2)
                    .to("mock:proxy")
                    .transform().simple("Bye ${body}\n");
            }
        };
    }
}
