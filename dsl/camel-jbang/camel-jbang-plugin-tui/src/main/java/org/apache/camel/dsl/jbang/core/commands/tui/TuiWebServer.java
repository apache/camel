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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.BindException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.tamboui.backend.aesh.AeshBackend;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.aesh.terminal.Connection;
import org.aesh.terminal.http.netty.HttpRequestHandler;
import org.aesh.terminal.http.netty.TtyWebSocketFrameHandler;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

/**
 * Serves the Camel TUI dashboard to a web browser over WebSocket, using Aesh's HTTP/WebSocket terminal bridge.
 * <p>
 * Each incoming connection gets its own {@link CamelMonitor} instance (running the same live-monitoring logic as a
 * local terminal session) driven by an {@link AeshBackend} wrapping that connection.
 * <p>
 * Binds to 127.0.0.1 only for security.
 */
class TuiWebServer {

    private static final Logger LOG = System.getLogger(TuiWebServer.class.getName());
    private final int port;
    private final CamelJBangMain main;
    private final ClassLoader classLoader;
    private final String name;
    private final long refreshInterval;
    private final String theme;
    private final ChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final ExecutorService sessionExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tui-web-session");
        t.setDaemon(true);
        return t;
    });
    private Channel serverChannel;
    private boolean stopped;

    TuiWebServer(int port, CamelJBangMain main, ClassLoader classLoader, String name, long refreshInterval,
                 String theme) {
        this.port = port;
        this.main = main;
        this.classLoader = classLoader;
        this.name = name;
        this.refreshInterval = refreshInterval;
        this.theme = theme;
    }

    void start() throws IOException {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            serverChannel = bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebServerInitializer())
                    .bind("127.0.0.1", port)
                    .sync()
                    .channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            throw new IOException("Interrupted while starting web terminal server", e);
        } catch (Exception e) {
            stop();
            Throwable cause = e.getCause();
            if (cause instanceof BindException bindException) {
                throw bindException;
            }
            if (e instanceof BindException bindException) {
                throw bindException;
            }
            throw new IOException("Failed to start web terminal server", e);
        }
    }

    synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        channels.close().syncUninterruptibly();
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        sessionExecutor.shutdownNow();
    }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return bossGroup.terminationFuture().await(timeout, unit)
                && workerGroup.terminationFuture().await(timeout, unit);
    }

    private void accept(Connection connection) {
        sessionExecutor.submit(() -> {
            try {
                AeshBackend backend = new AeshBackend(connection);
                CamelMonitor monitor = new CamelMonitor(main, classLoader);
                monitor.name = name;
                monitor.refreshInterval = refreshInterval;
                monitor.theme = theme;
                monitor.webBackend = backend;
                monitor.call();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Web TUI session ended with an error", e);
            } finally {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // connection already closing
                }
            }
        });
    }

    private final class WebServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new ChunkedWriteHandler());
            pipeline.addLast(new HttpObjectAggregator(65_536));
            pipeline.addLast(new OriginCheckingUpgradeHandler());
            pipeline.addLast(new HttpRequestHandler("/ws", "/tui/web"));
            pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
            pipeline.addLast(new TtyWebSocketFrameHandler(channels, TuiWebServer.this::accept));
        }
    }

    private final class OriginCheckingUpgradeHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
            if ("/ws".equalsIgnoreCase(request.uri()) && !isAllowedOrigin(request)) {
                context.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN))
                        .addListener(future -> context.close());
                return;
            }
            context.fireChannelRead(request.retain());
        }
    }

    private boolean isAllowedOrigin(FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        return origin == null || origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port);
    }
}
