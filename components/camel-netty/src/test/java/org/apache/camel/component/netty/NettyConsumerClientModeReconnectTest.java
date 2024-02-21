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

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConsumerClientModeReconnectTest extends BaseNettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(NettyConsumerClientModeReconnectTest.class);

    private MyServer server;

    public void startNettyServer() throws Exception {
        server = new MyServer(getPort());
        server.start();
    }

    public void shutdownServer() {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testNettyRouteServerNotStarted() throws Exception {
        try {
            MockEndpoint receive = context.getEndpoint("mock:receive", MockEndpoint.class);
            receive.expectedBodiesReceived("Bye Willem");

            LOG.info(">>> starting Camel route while Netty server is not ready");
            context.getRouteController().startRoute("client");

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .until(receive::isStarted);
            LOG.info(">>> starting Netty server");
            startNettyServer();

            LOG.info(">>> routing done");

            Awaitility.await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        } finally {
            LOG.info(">>> shutting down Netty server");
            shutdownServer();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&clientMode=true&reconnect=true&reconnectInterval=200")
                        .id("client")
                        .process(new Processor() {
                            public void process(final Exchange exchange) {
                                log.info("Processing exchange in Netty server {}", exchange);
                                String body = exchange.getIn().getBody(String.class);
                                exchange.getMessage().setBody("Bye " + body);
                            }
                        }).to("log:receive").to("mock:receive").noAutoStartup();
            }
        };
    }

    private static class MyServer {
        private int port;
        private ServerBootstrap bootstrap;
        private Channel channel;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;

        MyServer(int port) {
            this.port = port;
        }

        public void start() throws Exception {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer());

            ChannelFuture cf = bootstrap.bind(port).sync();
            channel = cf.channel();
        }

        public void shutdown() {
            channel.disconnect();
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static class ServerHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.write("Willem\r\n");
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.warn("Unhandled exception caught: {}", cause.getMessage(), cause);
            ctx.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            // Do nothing here
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }
    }

    private static class ServerInitializer extends ChannelInitializer<SocketChannel> {
        private static final StringDecoder DECODER = new StringDecoder();
        private static final StringEncoder ENCODER = new StringEncoder();
        private static final ServerHandler SERVERHANDLER = new ServerHandler();

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();

            // Add the text line codec combination first,
            pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
                    8192, Delimiters.lineDelimiter()));
            // the encoder and decoder are static as these are sharable
            pipeline.addLast("decoder", DECODER);
            pipeline.addLast("encoder", ENCODER);

            // and then business logic.
            pipeline.addLast("handler", SERVERHANDLER);
        }
    }

}
