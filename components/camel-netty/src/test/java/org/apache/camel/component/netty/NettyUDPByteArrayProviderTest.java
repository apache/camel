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
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyUDPByteArrayProviderTest extends BaseNettyTest {
    private static final String SEND_STRING = "ef3e00559f5faf0262f5ff0962d9008daa91001cd46b0fa9330ef0f3030fff250e46f72444d1cc501678c351e04b8004c"
            + "4000002080000fe850bbe011030000008031b031bfe9251305441593830354720020800050440ff";
    private static final int SEND_COUNT = 10;
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
                        channel.pipeline().addLast(new ByteArrayDecoder());
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
    public void testSendingRawByteMessage() throws Exception {
        createNettyUdpReceiver();
        bind();
        for (int i = 0; i < SEND_COUNT; ++i) {
            template.sendBody("direct:in", fromHexString(SEND_STRING));
        }
        stop();
        assertTrue("We should have received some datagrams", receivedCount > 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("netty:udp://localhost:{{port}}?sync=false&udpByteArrayCodec=true&udpConnectionlessSending=true");
            }
        };
    }

    public class UdpHandler extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket, List<Object> objects) throws Exception {
            objects.add(datagramPacket.content().retain());
        }
    }

    public class ContentHandler extends SimpleChannelInboundHandler<byte[]> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte [] s) throws Exception {
            ++receivedCount;
            assertEquals(SEND_STRING, byteArrayToHex(s));
        }
    }
}
