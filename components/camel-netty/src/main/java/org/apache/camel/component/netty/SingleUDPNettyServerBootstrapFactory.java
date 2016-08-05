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
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Suspendable;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramWorkerPool;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.jboss.netty.handler.ipfilter.IpV4Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleUDPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory, Suspendable {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleUDPNettyServerBootstrapFactory.class);
    private static final String LOOPBACK_INTERFACE = "lo";
    private static final String MULTICAST_SUBNET = "224.0.0.0/4";
    private ChannelGroup allChannels;
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelPipelineFactory pipelineFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ConnectionlessBootstrap connectionlessBootstrap;
    private NetworkInterface multicastNetworkInterface;
    private DatagramChannel datagramChannel;
    private Channel channel;
    private WorkerPool workerPool;

    public SingleUDPNettyServerBootstrapFactory() {
    }

    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleUDPNettyServerBootstrapFactory.class.getName());
    }

    public void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;

        this.allChannels = configuration.getChannelGroup() != null
            ? configuration.getChannelGroup()
            : new DefaultChannelGroup(SingleUDPNettyServerBootstrapFactory.class.getName());
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
        // noop
    }

    @Override
    protected void doSuspend() throws Exception {
        // noop
    }

    protected void startServerBootstrap() throws Exception {
        // create non-shared worker pool
        int count = configuration.getWorkerCount() > 0 ? configuration.getWorkerCount() : NettyHelper.DEFAULT_IO_THREADS;
        workerPool = new NioDatagramWorkerPool(Executors.newCachedThreadPool(), count);

        datagramChannelFactory = new NioDatagramChannelFactory(workerPool);

        connectionlessBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
        connectionlessBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        connectionlessBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        connectionlessBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        connectionlessBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        connectionlessBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        connectionlessBootstrap.setOption("child.broadcast", configuration.isBroadcast());
        connectionlessBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
        connectionlessBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());
        // only set this if user has specified
        if (configuration.getReceiveBufferSizePredictor() > 0) {
            connectionlessBootstrap.setOption("receiveBufferSizePredictorFactory",
                    new FixedReceiveBufferSizePredictorFactory(configuration.getReceiveBufferSizePredictor()));
        }
        if (configuration.getBacklog() > 0) {
            connectionlessBootstrap.setOption("backlog", configuration.getBacklog());
        }

        // set any additional netty options
        if (configuration.getOptions() != null) {
            for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                connectionlessBootstrap.setOption(entry.getKey(), entry.getValue());
            }
        }

        LOG.debug("Created ConnectionlessBootstrap {} with options: {}", connectionlessBootstrap, connectionlessBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        connectionlessBootstrap.setPipelineFactory(pipelineFactory);

        InetSocketAddress hostAddress = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        IpV4Subnet multicastSubnet = new IpV4Subnet(MULTICAST_SUBNET);

        if (multicastSubnet.contains(configuration.getHost())) {
            datagramChannel = (DatagramChannel)connectionlessBootstrap.bind(hostAddress);
            String networkInterface = configuration.getNetworkInterface() == null ? LOOPBACK_INTERFACE : configuration.getNetworkInterface();
            multicastNetworkInterface = NetworkInterface.getByName(networkInterface);
            ObjectHelper.notNull(multicastNetworkInterface, "No network interface found for '" + networkInterface + "'.");
            LOG.info("ConnectionlessBootstrap joining {}:{} using network interface: {}", new Object[]{configuration.getHost(), configuration.getPort(), multicastNetworkInterface.getName()});
            datagramChannel.joinGroup(hostAddress, multicastNetworkInterface).syncUninterruptibly();
            allChannels.add(datagramChannel);
        } else {
            LOG.info("ConnectionlessBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
            channel = connectionlessBootstrap.bind(hostAddress);
            allChannels.add(channel);
        }
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ConnectionlessBootstrap disconnecting from {}:{}", configuration.getHost(), configuration.getPort());

        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // close server external resources
        if (datagramChannelFactory != null) {
            datagramChannelFactory.releaseExternalResources();
            datagramChannelFactory = null;
        }

        // and then shutdown the thread pools
        if (workerPool != null) {
            workerPool.shutdown();
            workerPool = null;
        }
    }

}
