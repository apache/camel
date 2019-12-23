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
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleTCPNettyServerBootstrapFactory.class);
    private ChannelGroup allChannels;
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelInitializer<Channel> pipelineFactory;
    private ServerBootstrap serverBootstrap;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public SingleTCPNettyServerBootstrapFactory() {
    }

    @Override
    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelInitializer<Channel> pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName(), ImmediateEventExecutor.INSTANCE);
    }

    @Override
    public void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelInitializer<Channel> pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName(), ImmediateEventExecutor.INSTANCE);
    }

    @Override
    public void addChannel(Channel channel) {
        allChannels.add(channel);
    }

    @Override
    public void removeChannel(Channel channel) {
        allChannels.remove(channel);
    }

    @Override
    public void addConsumer(NettyConsumer consumer) {
        // does not allow sharing
    }

    @Override
    public void removeConsumer(NettyConsumer consumer) {
        // does not allow sharing
    }

    @Override
    protected void doStart() throws Exception {
        if (camelContext == null && threadFactory == null) {
            throw new IllegalArgumentException("Either CamelContext or ThreadFactory must be set on " + this);
        }
        startServerBootstrap();
    }

    @Override
    protected void doStop() throws Exception {
        stopServerBootstrap();
    }

    protected void startServerBootstrap() throws Exception {
        // prefer using explicit configured thread pools
        EventLoopGroup bg = configuration.getBossGroup();
        EventLoopGroup wg = configuration.getWorkerGroup();

        if (bg == null) {
            // create new pool which we should shutdown when stopping as its not shared
            bossGroup = new NettyServerBossPoolBuilder()
                    .withNativeTransport(configuration.isNativeTransport())
                    .withBossCount(configuration.getBossCount())
                    .withName("NettyServerTCPBoss")
                    .build();
            bg = bossGroup;
        }
        if (wg == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerGroup = new NettyWorkerPoolBuilder()
                    .withNativeTransport(configuration.isNativeTransport())
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyServerTCPWorker")
                    .build();
            wg = workerGroup;
        }

        serverBootstrap = new ServerBootstrap();
        if (configuration.isNativeTransport()) {
            serverBootstrap.group(bg, wg).channel(EpollServerSocketChannel.class);
        } else {
            serverBootstrap.group(bg, wg).channel(NioServerSocketChannel.class);
        }
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, configuration.isKeepAlive());
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, configuration.isTcpNoDelay());
        serverBootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        serverBootstrap.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());
        if (configuration.getBacklog() > 0) {
            serverBootstrap.option(ChannelOption.SO_BACKLOG, configuration.getBacklog());
        }

        Map<String, Object> options = configuration.getOptions();
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                String value = entry.getValue().toString();
                ChannelOption<Object> option = ChannelOption.valueOf(entry.getKey());
                //For all netty options that aren't of type String
                //TODO: find a way to add primitive Netty options without having to add them to the Camel registry.
                if (EndpointHelper.isReferenceParameter(value)) {
                    String name = value.substring(1);
                    Object o = CamelContextHelper.mandatoryLookup(camelContext, name);
                    serverBootstrap.option(option, o);
                } else {
                    serverBootstrap.option(option, value);
                }
            }
        }

        // set the pipeline factory, which creates the pipeline for each newly created channels
        serverBootstrap.childHandler(pipelineFactory);

        LOG.debug("Created ServerBootstrap {}", serverBootstrap);

        LOG.info("ServerBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
        ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort())).sync();
        channel = channelFuture.channel();
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ServerBootstrap unbinding from {}:{}", configuration.getHost(), configuration.getPort());

        LOG.trace("Closing {} channels", allChannels.size());
        allChannels.close().awaitUninterruptibly();

        // and then shutdown the thread pools
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

}
