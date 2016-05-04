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

import java.net.InetSocketAddress;
import java.util.List;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyUdpConnectionlessSendTest extends BaseNettyTest {
    private static final String SEND_STRING = "***<We all love camel>***";
    private static final int SEND_COUNT = 20;
    private volatile int receivedCount;
    private EventLoopGroup group;
    private Bootstrap bootstrap;

    public void createNettyUdpReceiver() {
        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast(new UdpHandler());
                        channel.pipeline().addLast(new ContentHandler());
                    }
                }).localAddress(new InetSocketAddress(getPort()));
    }


    public void bind() {
        bootstrap.bind().syncUninterruptibly();
    }

    public void stop() {
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    public void sendConnectionlessUdp() throws Exception {
        createNettyUdpReceiver();
        bind();
        for (int i = 0; i < SEND_COUNT; ++i) {
            template.sendBody("direct:in", SEND_STRING);
        }
        stop();
        assertTrue("We should have received some datagrams", receivedCount > 0);

    }

    @Test
    public void sendWithoutReceiver() throws Exception {
        int exceptionCount = 0;
        for (int i = 0; i < SEND_COUNT; ++i) {
            try {
                template.sendBody("direct:in", SEND_STRING);
            } catch (Exception ex) {
                ++exceptionCount;
            }
        }
        assertEquals("No exception should occur", 0, exceptionCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("netty4:udp://localhost:{{port}}?sync=false&textline=true&udpConnectionlessSending=true");
            }
        };
    }

    public class UdpHandler extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket, List<Object> objects) throws Exception {
            objects.add(datagramPacket.content().toString(CharsetUtil.UTF_8));
        }
    }

    public class ContentHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {
            ++receivedCount;
        }
    }
}
