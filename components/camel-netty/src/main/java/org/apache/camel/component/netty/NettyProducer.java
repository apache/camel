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
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyProducer extends DefaultAsyncProducer implements ServicePoolAware {
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyProducer.class);
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup("NettyProducer");
    private CamelContext context;
    private NettyConfiguration configuration;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ClientPipelineFactory pipelineFactory;
    private CamelLogger noReplyLogger;
    private ExecutorService bossExecutor;
    private ExecutorService workerExecutor;
    private final ChannelLocal<NettyCamelState> state = new ChannelLocal<NettyCamelState>();
    private ChannelFuture channelFuture;
    private Channel channel;

    public NettyProducer(NettyEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint);
        this.configuration = configuration;
        this.context = this.getEndpoint().getCamelContext();
        this.noReplyLogger = new CamelLogger(LOG, configuration.getNoReplyLogLevel());
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

    public CamelContext getContext() {
        return context;
    }

    protected boolean isTcp() {
        return configuration.getProtocol().equalsIgnoreCase("tcp");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // setup pipeline factory
        ClientPipelineFactory factory = configuration.getClientPipelineFactory();
        if (factory != null) {
            pipelineFactory = factory.createPipelineFactory(this);
        } else {
            pipelineFactory = new DefaultClientPipelineFactory(this);
        }

        if (isTcp()) {
            setupTCPCommunication();
        } else {
            setupUDPCommunication();
        }

        if (!configuration.isLazyChannelCreation()) {
            // ensure the connection can be established when we start up
            openAndCloseConnection();
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping producer at address: {}", configuration.getAddress());
        // close all channels
        LOG.trace("Closing {} channels", ALL_CHANNELS.size());
        ChannelGroupFuture future = ALL_CHANNELS.close();
        future.awaitUninterruptibly();

        // and then release other resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }

        // and then shutdown the thread pools
        if (bossExecutor != null) {
            context.getExecutorServiceManager().shutdownNow(bossExecutor);
        }
        if (workerExecutor != null) {
            context.getExecutorServiceManager().shutdownNow(workerExecutor);
        }

        super.doStop();
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(true);
            return true;
        }

        Object body = NettyPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            noReplyLogger.log("No payload to send for exchange: " + exchange);
            callback.done(true);
            return true;
        }

        // if textline enabled then covert to a String which must be used for textline
        if (getConfiguration().isTextline()) {
            try {
                body = NettyHelper.getTextlineBody(body, exchange, getConfiguration().getDelimiter(), getConfiguration().isAutoAppendDelimiter());
            } catch (NoTypeConversionAvailableException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        // set the exchange encoding property
        if (getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(getConfiguration().getCharsetName()));
        }

        try {
            // allow to reuse channel, on this producer, to avoid creating a new connection
            // for each message being sent
            if (channelFuture == null || channel == null || !channel.isOpen()) {
                channel = null;
                channelFuture = openConnection();
                channel = openChannel(channelFuture);
            }
            // setup state now we have the channel we can do this because
            // this producer is not thread safe, but pooled using ServicePoolAware
            state.set(channel, new NettyCamelState(callback, exchange));
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // log what we are writing
        LOG.debug("Writing body: {}", body);
        // write the body asynchronously
        ChannelFuture future = channel.write(body);

        // add listener which handles the operation
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                LOG.debug("Operation complete {}", channelFuture);
                if (!channelFuture.isSuccess()) {
                    // no success the set the caused exception and signal callback and break
                    exchange.setException(channelFuture.getCause());
                    callback.done(false);
                    return;
                }

                // if we do not expect any reply then signal callback to continue routing
                if (!configuration.isSync()) {
                    try {
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
                                LOG.debug("Closing channel when complete at address: {}", getEndpoint().getConfiguration().getAddress());
                            }
                            NettyHelper.close(channel);
                        }
                    } finally {
                        // signal callback to continue routing
                        callback.done(false);
                    }
                }
            }
        });

        // continue routing asynchronously
        return false;
    }

    /**
     * To get the {@link NettyCamelState} from this producer.
     */
    public NettyCamelState getState(Channel channel) {
        return state.get(channel);
    }

    /**
     * To remove the {@link NettyCamelState} stored on this producer,
     * when no longer needed
     */
    public void removeState(Channel channel) {
        state.remove(channel);
    }

    protected void setupTCPCommunication() throws Exception {
        if (channelFactory == null) {
            bossExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPBoss");
            workerExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyTCPWorker");
            if (configuration.getWorkerCount() <= 0) {
                channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor);
            } else {
                channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor, configuration.getWorkerCount());
            }
        }
    }

    protected void setupUDPCommunication() throws Exception {
        if (datagramChannelFactory == null) {
            workerExecutor = context.getExecutorServiceManager().newCachedThreadPool(this, "NettyUDPWorker");
            if (configuration.getWorkerCount() <= 0) {
                datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor);
            } else {
                datagramChannelFactory = new NioDatagramChannelFactory(workerExecutor, configuration.getWorkerCount());
            }
        }
    }

    private ChannelFuture openConnection() throws Exception {
        ChannelFuture answer;

        if (isTcp()) {
            ClientBootstrap clientBootstrap = new ClientBootstrap(channelFactory);
            clientBootstrap.setOption("keepAlive", configuration.isKeepAlive());
            clientBootstrap.setOption("tcpNoDelay", configuration.isTcpNoDelay());
            clientBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
            clientBootstrap.setOption("connectTimeoutMillis", configuration.getConnectTimeout());

            // set the pipeline factory, which creates the pipeline for each newly created channels
            clientBootstrap.setPipelineFactory(pipelineFactory);
            answer = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            LOG.trace("Created new TCP client bootstrap connecting to {}:{}", configuration.getHost(), configuration.getPort());
            return answer;
        } else {
            ConnectionlessBootstrap connectionlessClientBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
            connectionlessClientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            connectionlessClientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            connectionlessClientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            connectionlessClientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
            connectionlessClientBootstrap.setOption("child.broadcast", configuration.isBroadcast());
            connectionlessClientBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
            connectionlessClientBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

            // set the pipeline factory, which creates the pipeline for each newly created channels
            connectionlessClientBootstrap.setPipelineFactory(pipelineFactory);
            // bind and store channel so we can close it when stopping
            Channel channel = connectionlessClientBootstrap.bind(new InetSocketAddress(0));
            ALL_CHANNELS.add(channel);
            answer = connectionlessClientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            LOG.trace("Created new UDP client bootstrap connecting to {}:{}", configuration.getHost(), configuration.getPort());
            return answer;
        }
    }

    private Channel openChannel(ChannelFuture channelFuture) throws Exception {
        // wait until until the operation is complete
        final CountDownLatch latch = new CountDownLatch(1);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                LOG.debug("Operation complete {}", channelFuture);
                latch.countDown();
            }
        });
        // blocking for channel to be done
        LOG.trace("Waiting for operation to complete {}", channelFuture);
        latch.await();

        if (!channelFuture.isSuccess()) {
            // clear channel as we did not connect
            channel = null;
            throw new CamelException("Cannot connect to " + configuration.getAddress(), channelFuture.getCause());
        }
        channel = channelFuture.getChannel();
        // to keep track of all channels in use
        ALL_CHANNELS.add(channel);

        LOG.debug("Creating connector to address: {}", configuration.getAddress());
        return channel;
    }

    private void openAndCloseConnection() throws Exception {
        ChannelFuture future = openConnection();
        Channel channel = openChannel(future);
        NettyHelper.close(channel);
        ALL_CHANNELS.remove(channel);
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

    public ChannelGroup getAllChannels() {
        return ALL_CHANNELS;
    }
}
