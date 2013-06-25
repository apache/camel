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
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleUDPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleUDPNettyServerBootstrapFactory.class);
    private final ChannelGroup allChannels;
    private CamelContext camelContext;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelPipelineFactory pipelineFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ConnectionlessBootstrap connectionlessServerBootstrap;
    private Channel channel;
    private ExecutorService workerExecutor;

    public SingleUDPNettyServerBootstrapFactory() {
        this.allChannels = new DefaultChannelGroup(SingleUDPNettyServerBootstrapFactory.class.getName());
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
            workerExecutor = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "NettyUDPWorker");
        } else {
            workerExecutor = Executors.newCachedThreadPool();
        }

        if (configuration.getWorkerCount() <= 0) {
            datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
        } else {
            datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor, configuration.getWorkerCount());
        }

        connectionlessServerBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
        connectionlessServerBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        connectionlessServerBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        connectionlessServerBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        connectionlessServerBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        connectionlessServerBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        connectionlessServerBootstrap.setOption("child.broadcast", configuration.isBroadcast());
        connectionlessServerBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
        connectionlessServerBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());
        // only set this if user has specified
        if (configuration.getReceiveBufferSizePredictor() > 0) {
            connectionlessServerBootstrap.setOption("receiveBufferSizePredictorFactory",
                    new FixedReceiveBufferSizePredictorFactory(configuration.getReceiveBufferSizePredictor()));
        }
        if (configuration.getBacklog() > 0) {
            connectionlessServerBootstrap.setOption("backlog", configuration.getBacklog());
        }

        // set any additional netty options
        if (configuration.getOptions() != null) {
            for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                connectionlessServerBootstrap.setOption(entry.getKey(), entry.getValue());
            }
        }

        LOG.info("Created ConnectionlessBootstrap {} with options: {}", connectionlessServerBootstrap, connectionlessServerBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        connectionlessServerBootstrap.setPipelineFactory(pipelineFactory);

        channel = connectionlessServerBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // close server external resources
        if (datagramChannelFactory != null) {
            datagramChannelFactory.releaseExternalResources();
            datagramChannelFactory = null;
        }

        // and then shutdown the thread pools
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
