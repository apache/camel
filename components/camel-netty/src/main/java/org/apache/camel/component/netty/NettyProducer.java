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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

public class NettyProducer extends DefaultProducer implements ServicePoolAware {
    private static final transient Log LOG = LogFactory.getLog(NettyProducer.class);
    private final ChannelGroup allChannels;
    private CamelContext context;
    private NettyConfiguration configuration;
    private CountDownLatch countdownLatch;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private Channel channel;
    private ClientBootstrap clientBootstrap;
    private ConnectionlessBootstrap connectionlessClientBootstrap;
    private ClientPipelineFactory clientPipelineFactory;
    private ChannelPipeline clientPipeline;

    public NettyProducer(NettyEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint);
        this.configuration = configuration;
        this.context = this.getEndpoint().getCamelContext();
        this.allChannels = new DefaultChannelGroup("NettyProducer-" + nettyEndpoint.getEndpointUri());
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getProtocol().equalsIgnoreCase("udp")) {
            setupUDPCommunication();
        } else {
            setupTCPCommunication();
        }
        if (!configuration.isLazyChannelCreation()) {
            openConnection();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping producer at address: " + configuration.getAddress());
        }
        closeConnection();
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        if (channel == null && !configuration.isLazyChannelCreation()) {
            throw new IllegalStateException("Not started yet!");
        }
        if (channel == null || !channel.isConnected()) {
            openConnection();
        }

        Object body = NettyPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            LOG.warn("No payload to send for exchange: " + exchange);
            return; // exit early since nothing to write
        }

        if (configuration.isSync()) {
            // only initialize latch if we should get a response
            countdownLatch = new CountDownLatch(1);
        }

        // log what we are writing
        if (LOG.isDebugEnabled()) {
            Object out = body;
            if (body instanceof byte[]) {
                // byte arrays is not readable so convert to string
                out = exchange.getContext().getTypeConverter().convertTo(String.class, body);
            }
            LOG.debug("Writing body : " + out);
        }

        // write the body
        NettyHelper.writeBody(channel, null, body, exchange);

        if (configuration.isSync()) {
            boolean success = countdownLatch.await(configuration.getTimeout(), TimeUnit.MILLISECONDS);
            if (!success) {
                throw new ExchangeTimedOutException(exchange, configuration.getTimeout());
            }

            ClientChannelHandler handler = (ClientChannelHandler) clientPipeline.get("handler");
            if (handler.getCause() != null) {
                throw new CamelExchangeException("Error occurred in ClientChannelHandler", exchange, handler.getCause());
            } else if (!handler.isMessageReceived()) {
                // no message received
                throw new CamelExchangeException("No response received from remote server: " + configuration.getAddress(), exchange);
            } else {
                // set the result on either IN or OUT on the original exchange depending on its pattern
                if (ExchangeHelper.isOutCapable(exchange)) {
                    NettyPayloadHelper.setOut(exchange, handler.getMessage());
                } else {
                    NettyPayloadHelper.setIn(exchange, handler.getMessage());
                }
            }
        }

        // should channel be closed after complete?
        Boolean close;
        if (ExchangeHelper.isOutCapable(exchange)) {
            close = exchange.getOut().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        } else {
            close = exchange.getIn().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = getConfiguration().isDisconnect();
        if (close != null) {
            disconnect = close;
        }
        if (disconnect) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing channel when complete at address: " + getEndpoint().getConfiguration().getAddress());
            }
            NettyHelper.close(channel);
        }
    }

    protected void setupTCPCommunication() throws Exception {
        if (channelFactory == null) {
            ExecutorService bossExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyTCPBoss",
                    configuration.getCorePoolSize(), configuration.getMaxPoolSize());
            ExecutorService workerExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyTCPWorker",
                    configuration.getCorePoolSize(), configuration.getMaxPoolSize());
            channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor);
        }
        if (clientBootstrap == null) {
            clientBootstrap = new ClientBootstrap(channelFactory);
            clientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            clientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            clientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            clientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
        }
    }

    protected void setupUDPCommunication() throws Exception {
        if (datagramChannelFactory == null) {
            ExecutorService workerExecutor = context.getExecutorServiceStrategy().newThreadPool(this, "NettyUDPWorker",
                    configuration.getCorePoolSize(), configuration.getMaxPoolSize());
            datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
        }
        if (connectionlessClientBootstrap == null) {
            connectionlessClientBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
            connectionlessClientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            connectionlessClientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            connectionlessClientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            connectionlessClientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
            connectionlessClientBootstrap.setOption("child.broadcast", configuration.isBroadcast());
            connectionlessClientBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
            connectionlessClientBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

        }
    }

    private void openConnection() throws Exception {
        ChannelFuture channelFuture;

        // initialize client pipeline factory
        if (clientPipelineFactory == null) {
            clientPipelineFactory = new ClientPipelineFactory(this);
        }
        // must get the pipeline from the factory when opening a new connection
        clientPipeline = clientPipelineFactory.getPipeline();

        if (clientBootstrap != null) {
            clientBootstrap.setPipeline(clientPipeline);
            channelFuture = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        } else if (connectionlessClientBootstrap != null) {
            connectionlessClientBootstrap.setPipeline(clientPipeline);
            connectionlessClientBootstrap.bind(new InetSocketAddress(0));
            channelFuture = connectionlessClientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        } else {
            throw new IllegalStateException("Should either be TCP or UDP");
        }

        channelFuture.awaitUninterruptibly();
        if (!channelFuture.isSuccess()) {
            throw new CamelException("Cannot connect to " + configuration.getAddress(), channelFuture.getCause());
        }
        channel = channelFuture.getChannel();
        // to keep track of all channels in use
        allChannels.add(channel);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: " + configuration.getAddress());
        }
    }

    private void closeConnection() throws Exception {
        // close all channels
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // and then release other resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public CountDownLatch getCountdownLatch() {
        return countdownLatch;
    }

    public ChannelFactory getChannelFactory() {
        return channelFactory;
    }

    public void setChannelFactory(ChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public ClientBootstrap getClientBootstrap() {
        return clientBootstrap;
    }

    public void setClientBootstrap(ClientBootstrap clientBootstrap) {
        this.clientBootstrap = clientBootstrap;
    }

    public ClientPipelineFactory getClientPipelineFactory() {
        return clientPipelineFactory;
    }

    public void setClientPipelineFactory(ClientPipelineFactory clientPipelineFactory) {
        this.clientPipelineFactory = clientPipelineFactory;
    }

    public ChannelGroup getAllChannels() {
        return allChannels;
    }
}
