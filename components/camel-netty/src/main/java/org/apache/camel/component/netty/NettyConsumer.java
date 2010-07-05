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
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class NettyConsumer extends DefaultConsumer {
    private static final transient Log LOG = LogFactory.getLog(NettyConsumer.class);
    private final ChannelGroup allChannels;
    private CamelContext context;
    private NettyConfiguration configuration;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ServerBootstrap serverBootstrap;
    private ConnectionlessBootstrap connectionlessServerBootstrap;
    private Channel channel;

    public NettyConsumer(NettyEndpoint nettyEndpoint, Processor processor, NettyConfiguration configuration) {
        super(nettyEndpoint, processor);
        this.context = this.getEndpoint().getCamelContext();
        this.configuration = configuration;
        this.allChannels = new DefaultChannelGroup("NettyProducer-" + nettyEndpoint.getEndpointUri());
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Netty consumer binding to: " + configuration.getAddress());
        }

        super.doStart();
        if (isTcp()) {
            initializeTCPServerSocketCommunicationLayer();
        } else {
            initializeUDPServerSocketCommunicationLayer();
        }

        LOG.info("Netty consumer bound to: " + configuration.getAddress());
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Netty consumer unbinding from: " + configuration.getAddress());
        }

        // close all channels
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // and then release other resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }

        super.doStop();

        LOG.info("Netty consumer unbound from: " + configuration.getAddress());
    }

    public CamelContext getContext() {
        return context;
    }

    public ChannelGroup getAllChannels() {
        return allChannels;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public ChannelFactory getChannelFactory() {
        return channelFactory;
    }

    public void setChannelFactory(ChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public DatagramChannelFactory getDatagramChannelFactory() {
        return datagramChannelFactory;
    }

    public void setDatagramChannelFactory(DatagramChannelFactory datagramChannelFactory) {
        this.datagramChannelFactory = datagramChannelFactory;
    }

    public ServerBootstrap getServerBootstrap() {
        return serverBootstrap;
    }

    public void setServerBootstrap(ServerBootstrap serverBootstrap) {
        this.serverBootstrap = serverBootstrap;
    }

    public ConnectionlessBootstrap getConnectionlessServerBootstrap() {
        return connectionlessServerBootstrap;
    }

    public void setConnectionlessServerBootstrap(ConnectionlessBootstrap connectionlessServerBootstrap) {
        this.connectionlessServerBootstrap = connectionlessServerBootstrap;
    }

    protected boolean isTcp() {
        return configuration.getProtocol().equalsIgnoreCase("tcp");
    }

    private void initializeTCPServerSocketCommunicationLayer() throws Exception {
        ExecutorService bossExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyTCPBoss",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());
        ExecutorService workerExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyTCPWorker",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());

        channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
        serverBootstrap = new ServerBootstrap(channelFactory);
        serverBootstrap.setPipelineFactory(new ServerPipelineFactory(this));
        serverBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        serverBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        serverBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        serverBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());

        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    private void initializeUDPServerSocketCommunicationLayer() throws Exception {
        ExecutorService workerExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyUDPWorker",
                configuration.getCorePoolSize(), configuration.getMaxPoolSize());

        datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
        connectionlessServerBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
        connectionlessServerBootstrap.setPipelineFactory(new ServerPipelineFactory(this));
        connectionlessServerBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
        connectionlessServerBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
        connectionlessServerBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
        connectionlessServerBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        connectionlessServerBootstrap.setOption("child.broadcast", configuration.isBroadcast());
        connectionlessServerBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
        connectionlessServerBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

        channel = connectionlessServerBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

}
