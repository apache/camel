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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

public class NettyProducer extends DefaultProducer implements ServicePoolAware {
    private static final transient Log LOG = LogFactory.getLog(NettyProducer.class);
    private CamelContext context;
    private NettyConfiguration configuration;
    private CountDownLatch countdownLatch;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ChannelFuture channelFuture;
    private ClientBootstrap clientBootstrap;
    private ConnectionlessBootstrap connectionlessClientBootstrap;
    private ClientPipelineFactory clientPipelineFactory;
    private ChannelPipeline clientPipeline;

    public NettyProducer(NettyEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint);
        this.configuration = configuration;
        this.context = this.getEndpoint().getCamelContext();
    } 

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getProtocol().equalsIgnoreCase("udp")) {
            setupUDPCommunication();
        } else {
            setupTCPCommunication();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public boolean isSingleton() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return false;
    }

    public void process(Exchange exchange) throws Exception {
        if (configuration.isSync()) {
            countdownLatch = new CountDownLatch(1);
        }

        // write the body
        Channel channel = channelFuture.getChannel();
        NettyHelper.writeBody(channel, exchange.getIn().getBody(), exchange);

        if (configuration.isSync()) {
            boolean success = countdownLatch.await(configuration.getReceiveTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!success) {
                throw new ExchangeTimedOutException(exchange, configuration.getReceiveTimeoutMillis());
            }
            Object response = ((ClientChannelHandler) clientPipeline.get("handler")).getResponse();
            exchange.getOut().setBody(response);
        }                 
    }

    protected void setupTCPCommunication() throws Exception {
        if (channelFactory == null) {
            ExecutorService bossExecutor = 
                context.getExecutorServiceStrategy().newThreadPool(this, 
                    "NettyTCPBoss", 
                    configuration.getCorePoolSize(), 
                    configuration.getMaxPoolSize());
            ExecutorService workerExecutor = 
                context.getExecutorServiceStrategy().newThreadPool(this, 
                    "NettyTCPWorker", 
                    configuration.getCorePoolSize(), 
                    configuration.getMaxPoolSize());
            channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor);
        }
        if (clientBootstrap == null) {
            clientBootstrap = new ClientBootstrap(channelFactory);
            clientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            clientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            clientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            clientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeoutMillis());
        }
        if (clientPipelineFactory == null) {
            clientPipelineFactory = new ClientPipelineFactory(this);
            clientPipeline = clientPipelineFactory.getPipeline();
            clientBootstrap.setPipeline(clientPipeline);
        }
        channelFuture = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort())); 
        channelFuture.awaitUninterruptibly();
        LOG.info("Netty TCP Producer started and now listening on Host: " + configuration.getHost() + "Port : " + configuration.getPort());
    }
    
    protected void setupUDPCommunication() throws Exception {
        if (datagramChannelFactory == null) {
            ExecutorService workerExecutor = 
                context.getExecutorServiceStrategy().newThreadPool(this, 
                    "NettyUDPWorker", 
                    configuration.getCorePoolSize(), 
                    configuration.getMaxPoolSize());
            datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
        }
        if (connectionlessClientBootstrap == null) {
            connectionlessClientBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
            connectionlessClientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            connectionlessClientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            connectionlessClientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            connectionlessClientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeoutMillis());
            connectionlessClientBootstrap.setOption("child.broadcast", configuration.isBroadcast());
            connectionlessClientBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
            connectionlessClientBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

        }
        if (clientPipelineFactory == null) {
            clientPipelineFactory = new ClientPipelineFactory(this);
            clientPipeline = clientPipelineFactory.getPipeline();
            connectionlessClientBootstrap.setPipeline(clientPipeline);
        }
        connectionlessClientBootstrap.bind(new InetSocketAddress(0));
        channelFuture = connectionlessClientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort())); 
        channelFuture.awaitUninterruptibly();
        LOG.info("Netty UDP Producer started and now listening on Host: " + configuration.getHost() + "Port : " + configuration.getPort());
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

    public void setCountdownLatch(CountDownLatch countdownLatch) {
        this.countdownLatch = countdownLatch;
    }

    public ChannelFactory getChannelFactory() {
        return channelFactory;
    }

    public void setChannelFactory(ChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public ChannelFuture getChannelFuture() {
        return channelFuture;
    }

    public void setChannelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
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

    public ChannelPipeline getClientPipeline() {
        return clientPipeline;
    }

    public void setClientPipeline(ChannelPipeline clientPipeline) {
        this.clientPipeline = clientPipeline;
    }
    
}
