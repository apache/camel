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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class NettyProxyTest extends BaseNettyTest {

    @RegisterExtension
    protected AvailablePortFinder.Port port2 = AvailablePortFinder.find();

    @Test
    public void testNettyProxy() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:proxy").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");

        Object body = template.requestBody("netty:tcp://localhost:" + port.getPort() + "?sync=true&textline=true", "Camel\n");
        assertEquals("Bye Camel", body);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("netty:tcp://localhost:%s?sync=true&textline=true", port.getPort())
                        .to("mock:before")
                        .toF("netty:tcp://localhost:%s?sync=true&textline=true", port2.getPort())
                        .to("mock:after");

                fromF("netty:tcp://localhost:%s?sync=true&textline=true", port2.getPort())
                        .to("mock:proxy")
                        .transform().simple("Bye ${body}\n");
            }
        };
    }
}
