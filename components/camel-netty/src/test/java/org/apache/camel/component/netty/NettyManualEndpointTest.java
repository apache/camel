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
package org.apache.camel.component.netty;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

public class NettyManualEndpointTest extends BaseNettyTest {

    private NettyEndpoint endpoint;

    @Test
    public void testNettyManaul() throws Exception {
        assertNotNull(endpoint);

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody(endpoint, "Hello World\n");

        assertMockEndpointsSatisfied();

        assertEquals("netty:tcp://localhost:" + getPort(), endpoint.getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                NettyConfiguration nettyConfig = new NettyConfiguration();
                nettyConfig.setProtocol("tcp");
                nettyConfig.setHost("localhost");
                nettyConfig.setPort(getPort());
                nettyConfig.setSync(false);

                // need to add encoders and decoders manually
                nettyConfig.setEncoder(ChannelHandlerFactories.newStringEncoder(CharsetUtil.UTF_8));
                List<ChannelHandler> decoders = new ArrayList<ChannelHandler>();
                decoders.add(ChannelHandlerFactories.newDelimiterBasedFrameDecoder(1000, Delimiters.lineDelimiter()));
                decoders.add(ChannelHandlerFactories.newStringDecoder(CharsetUtil.UTF_8));
                nettyConfig.setDecoders(decoders);

                // create and start component
                NettyComponent component = new NettyComponent(getContext());
                component.setConfiguration(nettyConfig);
                getContext().addComponent("netty", component);
                component.start();

                // create and start endpoint, pass in null as endpoint uri
                // as we create this endpoint manually
                endpoint = new NettyEndpoint(null, component, nettyConfig);
                endpoint.setTimer(component.getTimer());
                endpoint.start();

                from(endpoint).to("mock:result");
            }
        };
    }
}
