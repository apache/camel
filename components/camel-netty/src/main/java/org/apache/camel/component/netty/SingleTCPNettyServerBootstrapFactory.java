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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ServiceSupport;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleTCPNettyServerBootstrapFactory.class);
    private final ChannelGroup allChannels;
    private CamelContext camelContext;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelPipelineFactory pipelineFactory;
    private ChannelFactory channelFactory;
    private ServerBootstrap serverBootstrap;
    private Channel channel;
    private ExecutorService bossExecutor;
    private ExecutorService workerExecutor;

    public SingleTCPNettyServerBootstrapFactory() {
        this.allChannels = new DefaultChannelGroup(SingleTCPNettyServerBootstrapFactory.class.getName());
    }

    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        // notice CamelContext can be optional
        this.camelContext = camelContext;
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
        startServerBootstrap();
    }

    @Override
    protected void doStop() throws Exception {
        stopServerBootstrap();
    }

    protected void startServerBootstrap() {
        if (camelContext != null) {
            bossExecutor = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPBoss");
            workerExecutor = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPWorker");

            if (configuration.getWorkerCount() <= 0) {
                channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
            } else {
                channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor,
                        configuration.getWorkerCount());
            }
        } else {
            if (configuration.getWorkerCount() <= 0) {
                channelFactory = new NioServerSocketChannelFactory();
            } else {
                channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool(), configuration.getWorkerCount());
            }
        }

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

        LOG.info("Created ServerBootstrap {} with options: {}", serverBootstrap, serverBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        serverBootstrap.setPipelineFactory(pipelineFactory);

        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // close server external resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
            channelFactory = null;
        }

        // and then shutdown the thread pools
        if (bossExecutor != null) {
            if (camelContext != null) {
                camelContext.getExecutorServiceManager().shutdown(bossExecutor);
            } else {
                bossExecutor.shutdownNow();
            }
            bossExecutor = null;
        }
        if (workerExecutor != null) {
            if (camelContext != null) {
                camelContext.getExecutorServiceManager().shutdown(workerExecutor);
            } else {
                workerExecutor.shutdownNow();
            }
            workerExecutor = null;
        }
    }
}
