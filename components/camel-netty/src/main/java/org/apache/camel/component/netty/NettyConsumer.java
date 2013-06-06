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

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyConsumer.class);
    private final ChannelGroup allChannels;
    private CamelContext context;
    private NettyConfiguration configuration;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ServerBootstrap serverBootstrap;
    private ConnectionlessBootstrap connectionlessServerBootstrap;
    private ServerPipelineFactory pipelineFactory;
    private Channel channel;
    private ExecutorService bossExecutor;
    private ExecutorService workerExecutor;

    public NettyConsumer(NettyEndpoint nettyEndpoint, Processor processor, NettyConfiguration configuration) {
        super(nettyEndpoint, processor);
        this.context = this.getEndpoint().getCamelContext();
        this.configuration = configuration;
        this.allChannels = new DefaultChannelGroup("NettyConsumer-" + nettyEndpoint.getEndpointUri());
        setExceptionHandler(new NettyConsumerExceptionHandler(this));
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Netty consumer binding to: {}", configuration.getAddress());

        // setup pipeline factory
        ServerPipelineFactory factory = configuration.getServerPipelineFactory();
        if (factory != null) {
            pipelineFactory = factory.createPipelineFactory(this);
        } else {
            pipelineFactory = new DefaultServerPipelineFactory(this);
        }

        if (isTcp()) {
            initializeTCPServerSocketCommunicationLayer();
        } else {
            initializeUDPServerSocketCommunicationLayer();
        }

        LOG.info("Netty consumer bound to: " + configuration.getAddress());
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Netty consumer unbinding from: {}", configuration.getAddress());

        // close all channels
        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // close server external resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }

        // and then shutdown the thread pools
        if (bossExecutor != null) {
            context.getExecutorServiceManager().shutdown(bossExecutor);
            bossExecutor = null;
        }
        if (workerExecutor != null) {
            context.getExecutorServiceManager().shutdown(workerExecutor);
            workerExecutor = null;
        }

        LOG.info("Netty consumer unbound from: " + configuration.getAddress());

        super.doStop();
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
        bossExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPBoss");
        workerExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPWorker");

        if (configuration.getWorkerCount() <= 0) {
            channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
        } else {
            channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor,
                                                               configuration.getWorkerCount());
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

        log.info("Created ServerBootstrap {} with options: {}", serverBootstrap, serverBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        serverBootstrap.setPipelineFactory(pipelineFactory);

        channel = serverBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

    private void initializeUDPServerSocketCommunicationLayer() throws Exception {
        workerExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyUDPWorker");
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

        log.info("Created ConnectionlessBootstrap {} with options: {}", connectionlessServerBootstrap, connectionlessServerBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        connectionlessServerBootstrap.setPipelineFactory(pipelineFactory);

        channel = connectionlessServerBootstrap.bind(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        // to keep track of all channels in use
        allChannels.add(channel);
    }

}
