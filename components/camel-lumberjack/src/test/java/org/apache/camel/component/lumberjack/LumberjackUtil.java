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
package org.apache.camel.component.lumberjack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLEngine;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.awaitility.Awaitility;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LumberjackUtil {
    private LumberjackUtil() {
    }

    static List<Integer> sendMessages(int port, SSLContextParameters sslContextParameters, List<Integer> windows)
            throws InterruptedException {
        return sendMessages(port, sslContextParameters, windows, true);
    }

    static List<Integer> sendMessages(
            int port, SSLContextParameters sslContextParameters, List<Integer> windows, boolean waitForResult)
            throws InterruptedException {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            // This list will hold the acknowledgment response sequence numbers
            List<Integer> responses = new ArrayList<>();

            // This initializer configures the SSL and an acknowledgment recorder
            ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (sslContextParameters != null) {
                        SSLEngine sslEngine = sslContextParameters.createSSLContext(sslContextParameters.getCamelContext())
                                .createSSLEngine();
                        sslEngine.setUseClientMode(true);
                        pipeline.addLast(new SslHandler(sslEngine));
                    }

                    // Add the response recorder
                    pipeline.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            assertEquals(msg.readUnsignedByte(), (short) '2');
                            assertEquals(msg.readUnsignedByte(), (short) 'A');
                            synchronized (responses) {
                                responses.add(msg.readInt());
                            }
                        }
                    });
                }
            };

            // Connect to the server
            Channel channel = new Bootstrap()                     //
                    .group(eventLoopGroup)                        //
                    .channel(NioSocketChannel.class)              //
                    .handler(initializer)                         //
                    .connect("127.0.0.1", port).sync().channel(); //

            // send 5 frame windows, without pausing
            windows.stream().forEach(window -> channel.writeAndFlush(readSample(String.format("io/window%s.bin", window))));
            if (waitForResult) {
                Awaitility.await().until(() -> windows.size() == responses.size());
            }

            channel.close();

            synchronized (responses) {
                return responses;
            }
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

    private static ByteBuf readSample(String resource) {
        try (InputStream stream = LumberjackUtil.class.getResourceAsStream(resource)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int input;
            while ((input = stream.read()) != -1) {
                output.write(input);
            }
            return wrappedBuffer(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeCamelException("Cannot read sample data", e);
        }
    }
}
