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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class NettyBinaryProxyTest extends BaseNettyTest {

    @RegisterExtension
    protected static AvailablePortFinder.Port port2 = AvailablePortFinder.find();

    @BindToRegistry("bytesDecoder")
    private final ChannelHandlerFactory bytesDecoder = ChannelHandlerFactories.newByteArrayDecoder("tcp");

    @BindToRegistry("bytesEncoder")
    private final ChannelHandlerFactory bytesEncoder = ChannelHandlerFactories.newByteArrayEncoder("tcp");

    @Test
    public void testNettyBinaryProxy() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Camel".getBytes());
        getMockEndpoint("mock:proxy").expectedBodiesReceived("Camel".getBytes());
        getMockEndpoint("mock:after").expectedBodiesReceived("Camel".getBytes());

        byte[] body = template.requestBody(
                "netty:tcp://localhost:%s?sync=true&disconnect=true&decoders=#bytesDecoder&encoders=#bytesEncoder"
                        .formatted(port.getPort()),
                "Camel".getBytes(), byte[].class);
        assertArrayEquals("Camel".getBytes(), body);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                DefaultRegistry registry = (DefaultRegistry) getContext().getRegistry();
                registry.bind("bytesDecoder", ChannelHandlerFactories.newByteArrayDecoder("tcp"));
                registry.bind("bytesEncoder", ChannelHandlerFactories.newByteArrayEncoder("tcp"));

                fromF("netty:tcp://0.0.0.0:%s?sync=true&disconnect=true&decoders=#bytesDecoder&encoders=#bytesEncoder",
                        port.getPort())
                        .to("mock:before")
                        .toF("netty:tcp://localhost:%s?sync=true&disconnect=true&decoders=#bytesDecoder&encoders=#bytesEncoder",
                                port2.getPort())
                        .to("mock:after");

                fromF("netty:tcp://0.0.0.0:%s?sync=true&disconnect=true&decoders=#bytesDecoder&encoders=#bytesEncoder",
                        port2.getPort())
                        .to("mock:proxy");
            }
        };
    }
}
