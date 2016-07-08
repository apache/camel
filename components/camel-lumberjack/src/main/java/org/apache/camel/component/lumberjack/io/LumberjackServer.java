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
package org.apache.camel.component.lumberjack.io;

import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines the Lumberjack server that will receive Lumberjack messages
 */
public final class LumberjackServer {
    private static final Logger LOG = LoggerFactory.getLogger(LumberjackServer.class);
    private static final int WORKER_THREADS = 16;

    private final String host;
    private final int port;
    private final SSLContext sslContext;
    private final ThreadFactory threadFactory;
    private final LumberjackMessageProcessor messageProcessor;

    private EventExecutorGroup executorService;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public LumberjackServer(String host, int port, SSLContext sslContext, ThreadFactory threadFactory,
                            LumberjackMessageProcessor messageProcessor) {
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
        this.threadFactory = threadFactory;
        this.messageProcessor = messageProcessor;
    }

    /**
     * Starts the server.
     *
     * @throws InterruptedException when interrupting while connecting the socket
     */
    public void start() throws InterruptedException {
        LOG.info("Starting the LUMBERJACK server (host={}, port={}).", host, port);

        // Create the group that will listen for incoming connections
        bossGroup = new NioEventLoopGroup(1);
        // Create the group that will process the connections
        workerGroup = new NioEventLoopGroup(WORKER_THREADS);
        // Create the executor service that will process the payloads without blocking netty threads
        executorService = new DefaultEventExecutorGroup(WORKER_THREADS, threadFactory);

        // Create the channel initializer
        ChannelHandler initializer = new LumberjackChannelInitializer(sslContext, executorService, messageProcessor);

        // Bootstrap the netty server
        ServerBootstrap serverBootstrap = new ServerBootstrap()  //
                .group(bossGroup, workerGroup)               //
                .channel(NioServerSocketChannel.class)           //
                .option(ChannelOption.SO_BACKLOG, 100)           //
                .childHandler(initializer);                      //

        // Connect the socket
        channel = serverBootstrap.bind(host, port).sync().channel();

        LOG.info("LUMBERJACK server is started (host={}, port={}).", host, port);
    }

    /**
     * Stops the server.
     *
     * @throws InterruptedException when interrupting while stopping the socket
     */
    public void stop() throws InterruptedException {
        LOG.info("Stopping the LUMBERJACK server (host={}, port={}).", host, port);

        try {
            // Wait for the channel to be indeed closed before shutting the groups & service
            channel.close().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            executorService.shutdownGracefully();
        }

        LOG.info("LUMBERJACK server is stopped (host={}, port={}).", host, port);
    }
}
