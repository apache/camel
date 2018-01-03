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
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.apache.camel.CamelContext;
import org.apache.camel.component.netty4.util.SubnetUtils;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class SingleUDPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(SingleUDPNettyServerBootstrapFactory.class);
    private static final String LOOPBACK_INTERFACE = "lo";
    private static final String MULTICAST_SUBNET = "224.0.0.0/4";
    private final ChannelGroup allChannels;
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelInitializer<Channel> pipelineFactory;
    private NetworkInterface multicastNetworkInterface;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public SingleUDPNettyServerBootstrapFactory() {
        this.allChannels = new DefaultChannelGroup(SingleUDPNettyServerBootstrapFactory.class.getName(), ImmediateEventExecutor.INSTANCE);
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

    protected void startServerBootstrap() throws Exception {
        // create non-shared worker pool
        EventLoopGroup wg = configuration.getWorkerGroup();
        if (wg == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerGroup = new NettyWorkerPoolBuilder()
                    .withNativeTransport(configuration.isNativeTransport())
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyServerTCPWorker")
                    .build();
            wg = workerGroup;
        }
        
        Bootstrap bootstrap = new Bootstrap();
        if (configuration.isNativeTransport()) {
            bootstrap.group(wg).channel(EpollDatagramChannel.class);
        } else {
            bootstrap.group(wg).channel(NioDatagramChannel.class);
        }
        // We cannot set the child option here      
        bootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        bootstrap.option(ChannelOption.SO_SNDBUF, configuration.getSendBufferSize());
        bootstrap.option(ChannelOption.SO_RCVBUF, configuration.getReceiveBufferSize());
        bootstrap.option(ChannelOption.SO_BROADCAST, configuration.isBroadcast());
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());
        
        // only set this if user has specified
        if (configuration.getReceiveBufferSizePredictor() > 0) {
            bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR,
                    new FixedRecvByteBufAllocator(configuration.getReceiveBufferSizePredictor()));
        }
        
        if (configuration.getBacklog() > 0) {
            bootstrap.option(ChannelOption.SO_BACKLOG, configuration.getBacklog());
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
                    bootstrap.option(option, o);
                } else {
                    bootstrap.option(option, value);
                }
            }
        }
        LOG.debug("Created Bootstrap {}", bootstrap);

        // set the pipeline factory, which creates the pipeline for each newly created channels
        bootstrap.handler(pipelineFactory);

        InetSocketAddress hostAddress = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        SubnetUtils multicastSubnet = new SubnetUtils(MULTICAST_SUBNET);

        if (multicastSubnet.getInfo().isInRange(configuration.getHost())) {
            ChannelFuture channelFuture = bootstrap.bind(configuration.getPort()).sync();
            channel = channelFuture.channel();
            DatagramChannel datagramChannel = (DatagramChannel) channel;
            String networkInterface = configuration.getNetworkInterface() == null ? LOOPBACK_INTERFACE : configuration.getNetworkInterface();
            multicastNetworkInterface = NetworkInterface.getByName(networkInterface);
            ObjectHelper.notNull(multicastNetworkInterface, "No network interface found for '" + networkInterface + "'.");
            LOG.info("ConnectionlessBootstrap joining {}:{} using network interface: {}", new Object[]{configuration.getHost(), configuration.getPort(), multicastNetworkInterface.getName()});
            datagramChannel.joinGroup(hostAddress, multicastNetworkInterface).syncUninterruptibly();
            allChannels.add(datagramChannel);
        } else {
            LOG.info("ConnectionlessBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
            ChannelFuture channelFuture = bootstrap.bind(hostAddress).sync();
            channel = channelFuture.channel();
            allChannels.add(channel);
        }
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ConnectionlessBootstrap disconnecting from {}:{}", configuration.getHost(), configuration.getPort());

        LOG.trace("Closing {} channels", allChannels.size());
        allChannels.close().awaitUninterruptibly();
        
        // and then shutdown the thread pools
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

}
