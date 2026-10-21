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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

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
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer with {@code clientMode=true} connects as a TCP client, so {@code hostnameVerification=true} applies to it
 * as well and has to check the certificate of the server it dials.
 * <p/>
 * The server below presents keystore-localhost.jks, whose only subject alternative name is DNS:localhost, and the
 * consumer connects to localhost - so the check passes and the message is routed. Without the peer on the engine the
 * JDK cannot run the check and fails the handshake instead, so nothing would reach the mock.
 */
@Isolated("Does not seem to run reliably in parallel")
@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
class NettySSLConsumerClientModeHostnameVerificationTest extends BaseNettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(NettySSLConsumerClientModeHostnameVerificationTest.class);
    private static final String KEYSTORE = "src/test/resources/keystore-localhost.jks";
    private static final char[] PASSWORD = "changeit".toCharArray();

    private MyServer server;

    @BindToRegistry("ksf")
    public File loadKeystoreKsf() {
        return new File(KEYSTORE);
    }

    @BindToRegistry("tsf")
    public File loadKeystoreTsf() {
        return new File(KEYSTORE);
    }

    @Test
    void verifiesTheHostOfTheServerItConnectsTo() throws Exception {
        server = new MyServer(getPort());
        try {
            server.start();
            MockEndpoint receive = context.getEndpoint("mock:receive", MockEndpoint.class);
            receive.expectedBodiesReceived("Bye Willem");
            context.getRouteController().startRoute("sslclient");
            receive.assertIsSatisfied();
        } finally {
            server.shutdown();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&clientMode=true&ssl=true&hostnameVerification=true"
                     + "&passphrase=changeit&keyStoreResource=#ksf&trustStoreResource=#tsf")
                        .id("sslclient")
                        .setBody(simple("Bye ${body}"))
                        .to("mock:receive")
                        .autoStartup(false);
            }
        };
    }

    private static SSLContext serverSslContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream in = new FileInputStream(new File(KEYSTORE))) {
            ks.load(in, PASSWORD);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext answer = SSLContext.getInstance("TLS");
        answer.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return answer;
    }

    /** Greets whoever connects, so the consumer has something to route. */
    private static final class MyServer {

        private final int port;
        private Channel channel;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;

        MyServer(int port) {
            this.port = port;
        }

        void start() throws Exception {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer());

            ChannelFuture cf = bootstrap.bind(port).sync();
            channel = cf.channel();
        }

        void shutdown() {
            if (channel != null) {
                channel.disconnect();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
    }

    private static final class ServerHandler extends SimpleChannelInboundHandler<String> {

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
            // the consumer is the one that routes, the server only greets
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }
    }

    private static final class ServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SSLContext sslContext;

        ServerInitializer() {
            try {
                sslContext = serverSslContext();
            } catch (Exception e) {
                throw new IllegalStateException("Could not create the server SSLContext", e);
            }
        }

        @Override
        public void initChannel(SocketChannel ch) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);

            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("ssl", new SslHandler(engine));
            pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
            pipeline.addLast("decoder", new StringDecoder());
            pipeline.addLast("encoder", new StringEncoder());
            pipeline.addLast("handler", new ServerHandler());
        }
    }
}
