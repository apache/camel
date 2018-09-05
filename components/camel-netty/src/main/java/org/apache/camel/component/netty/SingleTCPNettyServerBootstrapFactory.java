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
package org.apache.camel.component.netty;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Suspendable;
import org.apache.camel.support.ServiceSupport;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory, Suspendable {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleTCPNettyServerBootstrapFactory.class);
    private ChannelGroup allChannels;
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelPipelineFactory pipelineFactory;
    private ChannelFactory channelFactory;
    private ServerBootstrap serverBootstrap;
    private Channel channel;
    private BossPool bossPool;
    private WorkerPool workerPool;

    public SingleTCPNettyServerBootstrapFactory() {
    }

    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName());
    }

    public void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName());
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
                channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
                allChannels.add(channel);
            }
        }
    }

    @Override
    protected void doSuspend() throws Exception {
        if (channel != null) {
            LOG.debug("ServerBootstrap unbinding from {}:{}", configuration.getHost(), configuration.getPort());
            ChannelFuture future = channel.unbind();
            future.awaitUninterruptibly();
        }
    }

    protected void startServerBootstrap() {
        // prefer using explicit configured thread pools
        BossPool bp = configuration.getBossPool();
        WorkerPool wp = configuration.getWorkerPool();

        if (bp == null) {
            // create new pool which we should shutdown when stopping as its not shared
            bossPool = new NettyServerBossPoolBuilder()
                    .withBossCount(configuration.getBossCount())
                    .withName("NettyServerTCPBoss")
                    .build();
            bp = bossPool;
        }
        if (wp == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerPool = new NettyWorkerPoolBuilder()
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyServerTCPWorker")
                    .build();
            wp = workerPool;
        }

        channelFactory = new NioServerSocketChannelFactory(bp, wp);

        serverBootstrap = new ServerBootstrap(channelFactory);
        serverBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        serverBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        serverBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        serverBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        serverBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        if (configuration.getBacklog() > 0) {
            serverBootstrap.setOption("backlog", configuration.getBacklog());
        }

        // set any additional netty options
        if (configuration.getOptions() != null) {
            for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                serverBootstrap.setOption(entry.getKey(), entry.getValue());
            }
        }

        LOG.debug("Created ServerBootstrap {} with options: {}", serverBootstrap, serverBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        serverBootstrap.setPipelineFactory(pipelineFactory);

        LOG.info("ServerBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ServerBootstrap unbinding from {}:{}", configuration.getHost(), configuration.getPort());

        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // close server external resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
            channelFactory = null;
        }

        // and then shutdown the thread pools
        if (bossPool != null) {
            bossPool.shutdown();
            bossPool = null;
        }
        if (workerPool != null) {
            workerPool.shutdown();
            workerPool = null;
        }
    }

}
