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
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class NettyConsumer extends DefaultConsumer {
    private static final transient Log LOG = LogFactory.getLog(NettyConsumer.class);
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
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getProtocol().equalsIgnoreCase("udp")) {
            initializeUDPServerSocketCommunicationLayer();
        } else {
            initializeTCPServerSocketCommunicationLayer();
        }

        LOG.info("Netty consumer bound to: " + configuration.getAddress());
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isInfoEnabled()) {
            LOG.info("Netty consumer unbinding from: " + configuration.getAddress());
        }


        if (channel != null) {
            NettyHelper.close(channel);
        }

        // TODO: use ChannelGroup to keep track on open connections etc to be closed on stopping
        // and then releasing channel factory would be faster
//        if (channelFactory != null) {
//            channelFactory.releaseExternalResources();
//        }

        super.doStop();
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
        serverBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeoutMillis());

        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
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
        connectionlessServerBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeoutMillis());
        connectionlessServerBootstrap.setOption("child.broadcast", configuration.isBroadcast());
        connectionlessServerBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
        connectionlessServerBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

        channel = connectionlessServerBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
    }

}
