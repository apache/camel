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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.tamboui.backend.jline3.JLineBackend;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
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
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.jline.terminal.Terminal;

/**
 * Serves the Camel TUI dashboard to a web browser over WebSocket: the page ({@link WebPageHandler}), then a terminal
 * per browser session ({@link WebTerminal}).
 * <p>
 * Each session gets its own {@link CamelMonitor} instance (running the same live-monitoring logic as a local terminal
 * session) on a {@link JLineBackend}, as a local session runs (CAMEL-25581).
 * <p>
 * Binds to 127.0.0.1 only for security.
 */
class TuiWebServer {

    private static final Logger LOG = System.getLogger(TuiWebServer.class.getName());
    private static final int SESSION_QUEUE_CAPACITY = 64;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    private final int port;
    private final CamelJBangMain main;
    private final ClassLoader classLoader;
    private final String name;
    private final long refreshInterval;
    private final String theme;
    private final ChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final ExecutorService sessionExecutor = new ThreadPoolExecutor(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(SESSION_QUEUE_CAPACITY), r -> {
                Thread t = new Thread(r, "tui-web-session");
                t.setDaemon(true);
                return t;
            });
    private final Consumer<Terminal> sessionHandler;
    private Channel serverChannel;
    private boolean stopped;

    TuiWebServer(int port, CamelJBangMain main, ClassLoader classLoader, String name, long refreshInterval,
                 String theme) {
        this(port, main, classLoader, name, refreshInterval, theme, null);
    }

    /**
     * Package-private, for tests: lets a lightweight session handler replace the default one, which spawns a full
     * {@link CamelMonitor}.
     */
    TuiWebServer(int port, CamelJBangMain main, ClassLoader classLoader, String name, long refreshInterval,
                 String theme, Consumer<Terminal> sessionHandler) {
        this.port = port;
        this.main = main;
        this.classLoader = classLoader;
        this.name = name;
        this.refreshInterval = refreshInterval;
        this.theme = theme;
        this.sessionHandler = sessionHandler != null ? sessionHandler : this::runSession;
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
        bossGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).syncUninterruptibly();
        workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).syncUninterruptibly();
        sessionExecutor.shutdown();
        try {
            if (!sessionExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                sessionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sessionExecutor.shutdownNow();
        }
    }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        boolean bossDone = bossGroup.terminationFuture().await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        boolean workerDone
                = workerGroup.terminationFuture().await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        boolean sessionDone
                = sessionExecutor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        return bossDone && workerDone && sessionDone;
    }

    private boolean startSession(Terminal terminal, Runnable ended) {
        try {
            sessionExecutor.submit(() -> {
                try {
                    sessionHandler.accept(terminal);
                } finally {
                    closeQuietly(terminal);
                    ended.run();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            LOG.log(Level.WARNING, "Web TUI session rejected: server is stopping or too many concurrent sessions");
            closeQuietly(terminal);
            return false;
        }
    }

    private void runSession(Terminal terminal) {
        try {
            CamelMonitor monitor = new CamelMonitor(main, classLoader);
            monitor.name = name;
            monitor.refreshInterval = refreshInterval;
            monitor.theme = theme;
            monitor.webBackend = new JLineBackend(terminal);
            monitor.call();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Web TUI session ended with an error", e);
        }
    }

    private static void closeQuietly(Terminal terminal) {
        try {
            terminal.close();
        } catch (Exception ignored) {
            // the terminal is already closed
        }
    }

    private final class WebServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65_536));
            pipeline.addLast(new OriginCheckingUpgradeHandler());
            pipeline.addLast(new SecurityHeadersHandler());
            pipeline.addLast(new WebPageHandler("/ws"));
            pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
            pipeline.addLast(new WebTerminal(channels, TuiWebServer.this::startSession));
        }
    }

    private final class OriginCheckingUpgradeHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
            String path = new QueryStringDecoder(request.uri()).path();
            if ("/ws".equalsIgnoreCase(path) && !isAllowedOrigin(request)) {
                context.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN))
                        .addListener(future -> context.close());
                return;
            }
            context.fireChannelRead(request.retain());
        }
    }

    /**
     * Denies framing of the served pages, mitigating clickjacking against the loopback web TUI.
     */
    private static final class SecurityHeadersHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) {
            if (msg instanceof HttpResponse response) {
                response.headers().set("X-Frame-Options", "DENY");
                response.headers().set("Content-Security-Policy", "frame-ancestors 'none'");
            }
            context.write(msg, promise);
        }
    }

    private boolean isAllowedOrigin(FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        // A missing Origin header is allowed on purpose: non-browser clients (curl, custom
        // terminal clients) don't send one, and the loopback-only bind is the actual boundary here.
        return origin == null || origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port);
    }
}
