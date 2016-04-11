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

import java.net.InetSocketAddress;

import org.apache.camel.builder.RouteBuilder;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

//We need to run the tests with fix order
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NettyUdpConnectedSendTest extends BaseNettyTest {
    private static final String SEND_STRING = "***<We all love camel>***";
    private static final int SEND_COUNT = 20;
    private volatile int receivedCount;
    private ConnectionlessBootstrap bootstrap;

    public void createNettyUdpReceiver() {
        bootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory());
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline channelPipeline = Channels.pipeline();
                channelPipeline.addLast("StringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                channelPipeline.addLast("ContentHandler", new ContentHandler());
                return channelPipeline;
            }
        });
    }


    public void bind() {
        bootstrap.bind(new InetSocketAddress(getPort()));
    }

    public void stop() {
        bootstrap.shutdown();
    }

    @Test
    public void sendConnectedUdp() throws Exception {
        createNettyUdpReceiver();
        bind();
        for (int i = 0; i < SEND_COUNT; ++i) {
            template.sendBody("direct:in", SEND_STRING);
        }
        stop();
        assertTrue("We should have received some datagrams", receivedCount > 0);
    }

    @Test
    @Ignore("This test would be failed in JDK7 sometimes")
    public void sendConnectedWithoutReceiver() throws Exception {
        int exceptionCount = 0;
        for (int i = 0; i < SEND_COUNT; ++i) {
            try {
                template.sendBody("direct:in", SEND_STRING);
            } catch (Exception ex) {
                ++exceptionCount;
            }
        }
        assertTrue("There should at least one exception because port is unreachable", exceptionCount > 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("netty:udp://localhost:{{port}}?sync=false&textline=true");
            }
        };
    }

    public class ContentHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
            String s = (String)messageEvent.getMessage();
            receivedCount++;
            assertEquals(SEND_STRING, s.trim());
        }
    }
}
