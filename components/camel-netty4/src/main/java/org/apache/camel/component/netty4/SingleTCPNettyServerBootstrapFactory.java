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
import java.util.concurrent.ThreadFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleTCPNettyServerBootstrapFactory.class);
    private final ChannelGroup allChannels;
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelInitializer<Channel> pipelineFactory;
    private ServerBootstrap serverBootstrap;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public SingleTCPNettyServerBootstrapFactory() {
        // The executor just execute tasks in the callers thread
        this.allChannels = new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName(), ImmediateEventExecutor.INSTANCE);
    }

    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelInitializer<Channel> pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    public void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelInitializer<Channel> pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    public void addChannel(Channel channel) {
        allChannels.add(channel);
    }

    public void removeChannel(Channel channel) {
        allChannels.remove(channel);
    }

    public void addConsumer(NettyConsumer consumer) {
        // does not allow sharing
    }

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

    @Override
    protected void doResume() throws Exception {
        if (channel != null) {
            LOG.debug("ServerBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
            ChannelFuture future = channel.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                // if we cannot bind, the re-create channel
                allChannels.remove(channel);
                future = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort())).sync();
                channel = future.channel();
                allChannels.add(channel);
            }
        }
    }

    @Override
    protected void doSuspend() throws Exception {
        if (channel != null) {
            LOG.debug("ServerBootstrap unbinding from {}:{}", configuration.getHost(), configuration.getPort());
            //TODO need to check if it's good way to unbinding the channel
            ChannelFuture future = channel.close();
            future.awaitUninterruptibly();
        }
    }

    protected void startServerBootstrap() throws Exception {
        // prefer using explicit configured thread pools
        EventLoopGroup bg = configuration.getBossGroup();
        EventLoopGroup wg = configuration.getWorkerGroup();

        if (bg == null) {
            // create new pool which we should shutdown when stopping as its not shared
            bossGroup = new NettyServerBossPoolBuilder()
                    .withBossCount(configuration.getBossCount())
                    .withName("NettyServerTCPBoss")
                    .build();
            bg = bossGroup;
        }
        if (wg == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerGroup = new NettyWorkerPoolBuilder()
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyServerTCPWorker")
                    .build();
            wg = workerGroup;
        }
        
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bg, wg).channel(NioServerSocketChannel.class);
        serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, configuration.isKeepAlive());
        serverBootstrap.childOption(ChannelOption.TCP_NODELAY, configuration.isTcpNoDelay());
        serverBootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        serverBootstrap.childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());
        if (configuration.getBacklog() > 0) {
            serverBootstrap.option(ChannelOption.SO_BACKLOG, configuration.getBacklog());
        }

        // TODO set any additional netty options and child options
        /*if (configuration.getOptions() != null) {
            for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                serverBootstrap.setOption(entry.getKey(), entry.getValue());
            }
        }*/

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
