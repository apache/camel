/*
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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(NettyProducer.class);

    private static final AttributeKey<NettyCamelStateCorrelationManager> CORRELATION_MANAGER_ATTR
            = AttributeKey.valueOf("NettyCamelStateCorrelationManager");

    private ChannelGroup allChannels;
    private CamelContext context;
    private NettyConfiguration configuration;
    private ClientInitializerFactory pipelineFactory;
    private CamelLogger noReplyLogger;
    private EventLoopGroup workerGroup;
    private volatile ObjectPool<ChannelFuture> pool;
    private NettyCamelStateCorrelationManager correlationManager;

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

    public CamelContext getContext() {
        return context;
    }

    public NettyCamelStateCorrelationManager getCorrelationManager() {
        return correlationManager;
    }

    protected boolean isTcp() {
        return configuration.getProtocol().equalsIgnoreCase("tcp");
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.isProducerPoolEnabled()) {
            // setup pool where we want an unbounded pool, which allows the pool to shrink on no demand
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config.setMaxTotal(configuration.getProducerPoolMaxTotal());
            config.setMinIdle(configuration.getProducerPoolMinIdle());
            config.setMaxIdle(configuration.getProducerPoolMaxIdle());
            config.setBlockWhenExhausted(configuration.isProducerPoolBlockWhenExhausted());
            config.setMaxWait(Duration.ofMillis(configuration.getProducerPoolMaxWait()));
            // we should test on borrow to ensure the channel is still valid
            config.setTestOnBorrow(true);
            // idle channels can be evicted
            config.setTestWhileIdle(false);
            // run eviction every 30th second
            config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
            config.setMinEvictableIdleTime(Duration.ofMillis(configuration.getProducerPoolMinEvictableIdle()));
            pool = new GenericObjectPool(new NettyProducerPoolableObjectFactory(this), config);

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Created NettyProducer pool[maxTotal={}, minIdle={}, maxIdle={}, minEvictableIdleDuration={}] -> {}",
                        config.getMaxTotal(), config.getMaxIdle(), config.getMaxIdle(), config.getMinEvictableIdleDuration(),
                        pool);
            }
        } else {
            pool = new SharedSingletonObjectPool<>(new NettyProducerPoolableObjectFactory(this));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created NettyProducer shared singleton pool -> {}", pool);
            }
        }

        if (configuration.getWorkerGroup() == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerGroup = new NettyWorkerPoolBuilder()
                    .withNativeTransport(configuration.isNativeTransport())
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyClientTCPWorker").build();
        }

        // setup pipeline factory
        ClientInitializerFactory factory = configuration.getClientInitializerFactory();
        if (factory != null) {
            pipelineFactory = factory.createPipelineFactory(this);
        } else {
            pipelineFactory = new DefaultClientInitializerFactory(this);
        }

        // setup channel group
        if (configuration.getChannelGroup() == null) {
            allChannels = new DefaultChannelGroup("NettyProducer", ImmediateEventExecutor.INSTANCE);
        } else {
            allChannels = configuration.getChannelGroup();
        }

        if (!configuration.isLazyChannelCreation()) {
            // ensure the connection can be established when we start up
            ChannelFuture channelFuture = pool.borrowObject();
            channelFuture.get();
            pool.returnObject(channelFuture);
        }

        if (configuration.getCorrelationManager() != null) {
            correlationManager = configuration.getCorrelationManager();
        } else {
            correlationManager = new DefaultNettyCamelStateCorrelationManager();
        }
        if (correlationManager instanceof CamelContextAware) {
            ((CamelContextAware) correlationManager).setCamelContext(getContext());
        }
        ServiceHelper.startService(correlationManager);

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping producer at address: {}", configuration.getAddress());

        if (pool != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping producer with channel pool[active={}, idle={}]", pool.getNumActive(), pool.getNumIdle());
            }
            pool.close();
        }

        // close all channels
        LOG.debug("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // and then shutdown the thread pools
        if (workerGroup != null) {
            LOG.debug("Stopping worker group: {}", workerGroup);
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        LOG.trace("Stopping correlation manager: {}", correlationManager);
        ServiceHelper.stopService(correlationManager);

        LOG.debug("Stopped producer at address: {}", configuration.getAddress());
        super.doStop();
    }

    @Override
    public boolean process(final Exchange exchange, AsyncCallback callback) {
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(true);
            return true;
        }

        Object body;
        try {
            body = getRequestBody(exchange);
            if (body == null) {
                noReplyLogger.log("No payload to send for exchange: " + exchange);
                callback.done(true);
                return true;
            }
            return processWithBody(exchange, body, new BodyReleaseCallback(callback, body));
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    private boolean processWithBody(final Exchange exchange, Object body, BodyReleaseCallback callback) {

        // set the exchange encoding property
        if (getConfiguration().getCharsetName() != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME,
                    IOHelper.normalizeCharset(getConfiguration().getCharsetName()));
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Pool[active={}, idle={}]", pool.getNumActive(), pool.getNumIdle());
        }

        // get a channel from the pool
        ChannelFuture channelFuture;
        Channel channel = null;
        try {
            if (getConfiguration().isReuseChannel()) {
                channel = exchange.getProperty(NettyConstants.NETTY_CHANNEL, Channel.class);
            }
            if (channel == null) {
                if (pool == null) {
                    throw new IllegalStateException("Producer pool is null");
                }
                channelFuture = pool.borrowObject();
                if (channelFuture != null) {
                    LOG.trace("Got channel request from pool {}", channelFuture);
                }
            } else {
                channelFuture = channel.newSucceededFuture();
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // we must have a channel
        if (channelFuture == null) {
            exchange.setException(new CamelExchangeException("Cannot get channel from pool", exchange));
            callback.done(true);
            return true;
        }

        channelFuture.addListener(new ChannelConnectedListener(exchange, callback, body));
        return false;
    }

    public void processWithConnectedChannel(
            final Exchange exchange, final BodyReleaseCallback callback, final ChannelFuture channelFuture, final Object body) {
        // remember channel so we can reuse it
        final Channel channel = channelFuture.channel();
        if (getConfiguration().isReuseChannel() && exchange.getProperty(NettyConstants.NETTY_CHANNEL) == null) {
            // remember correlation manager for this channel
            // for use when sending subsequent messages reusing this channel
            channel.attr(CORRELATION_MANAGER_ATTR).set(correlationManager);
            exchange.setProperty(NettyConstants.NETTY_CHANNEL, channel);
            // and defer closing the channel until we are done routing the exchange
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
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
                        LOG.trace("Closing channel {} as routing the Exchange is done", channel);
                        NettyHelper.close(channel);
                    }

                    releaseChannel(channelFuture);
                }
            });
        }

        // Get appropriate correlation manager.
        // If we reuse channel then get it from channel. CORRELATION_MANAGER_ATTR should be set at this point.
        // Otherwise use correlation manager for this producer.
        final NettyCamelStateCorrelationManager channelCorrelationManager
                = Optional.ofNullable(channel.attr(CORRELATION_MANAGER_ATTR).get()).orElse(correlationManager);

        if (exchange.getIn().getHeader(NettyConstants.NETTY_REQUEST_TIMEOUT) != null) {
            long timeoutInMs = exchange.getIn().getHeader(NettyConstants.NETTY_REQUEST_TIMEOUT, Long.class);
            ChannelHandler oldHandler = channel.pipeline().get("timeout");
            ReadTimeoutHandler newHandler = new ReadTimeoutHandler(timeoutInMs, TimeUnit.MILLISECONDS);
            if (oldHandler == null) {
                channel.pipeline().addBefore("handler", "timeout", newHandler);
            } else {
                channel.pipeline().replace(oldHandler, "timeout", newHandler);
            }
        }

        //This will refer to original callback since netty will release body by itself
        final AsyncCallback producerCallback;

        if (configuration.isReuseChannel()) {
            // use callback as-is because we should not put it back in the pool as NettyProducerCallback would do
            // as when reuse channel is enabled it will put the channel back in the pool when exchange is done using on completion
            producerCallback = callback.getOriginalCallback();
        } else {
            producerCallback = new NettyProducerCallback(channelFuture, callback.getOriginalCallback());
        }

        // setup state as attachment on the channel, so we can access the state later when needed
        final NettyCamelState state = new NettyCamelState(producerCallback, exchange);
        channelCorrelationManager.putState(channel, state);
        // here we need to setup the remote address information here
        InetSocketAddress remoteAddress = null;
        if (!isTcp()) {
            remoteAddress = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        }

        // write body
        NettyHelper.writeBodyAsync(LOG, channel, remoteAddress, body, new ChannelFutureListener() {
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                LOG.trace("Operation complete {}", channelFuture);
                if (!channelFuture.isSuccess()) {
                    Throwable cause = null;
                    // no success then exit, (any exception has been handled by ClientChannelHandler#exceptionCaught)
                    try {
                        // need to get real caused exception from netty, which is not possible in a nice API
                        // but we can try to get a result with a 0 timeout, then netty will throw the caused
                        // exception wrapped in an outer exception
                        channelFuture.get(0, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cause = e.getCause();
                    } catch (Exception e) {
                        cause = e.getCause();
                    }
                    if (cause != null) {
                        exchange.setException(cause);
                    }
                    state.onExceptionCaughtOnce(false);
                    return;
                }

                // if we do not expect any reply then signal callback to continue routing
                if (!configuration.isSync()) {
                    try {
                        // should channel be closed after complete?
                        Boolean close;
                        if (ExchangeHelper.isOutCapable(exchange)) {
                            close = exchange.getOut().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE,
                                    Boolean.class);
                        } else {
                            close = exchange.getIn().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
                        }

                        // should we disconnect, the header can override the configuration
                        boolean disconnect = getConfiguration().isDisconnect();
                        if (close != null) {
                            disconnect = close;
                        }

                        // we should not close if we are reusing the channel
                        if (!configuration.isReuseChannel() && disconnect) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Closing channel when complete at address: {}",
                                        getEndpoint().getConfiguration().getAddress());
                            }
                            NettyHelper.close(channel);
                        }
                    } finally {
                        // signal callback to continue routing
                        producerCallback.done(false);
                    }
                }
            }
        });

    }

    /**
     * Gets the object we want to use as the request object for sending to netty.
     *
     * @param  exchange  the exchange
     * @return           the object to use as request
     * @throws Exception is thrown if error getting the request body
     */
    protected Object getRequestBody(Exchange exchange) throws Exception {
        Object body = NettyPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            return null;
        }

        // if textline enabled then covert to a String which must be used for textline
        if (getConfiguration().isTextline()) {
            body = NettyHelper.getTextlineBody(body, exchange, getConfiguration().getDelimiter(),
                    getConfiguration().isAutoAppendDelimiter());
        }

        return body;
    }

    protected EventLoopGroup getWorkerGroup() {
        // prefer using explicit configured thread pools
        EventLoopGroup wg = configuration.getWorkerGroup();
        if (wg == null) {
            wg = workerGroup;
        }
        return wg;
    }

    protected ChannelFuture openConnection() throws Exception {
        ChannelFuture answer;

        if (isTcp()) {
            // its okay to create a new bootstrap for each new channel
            Bootstrap clientBootstrap = new Bootstrap();
            if (configuration.getUnixDomainSocketPath() != null) {
                if (KQueue.isAvailable()) {
                    clientBootstrap.channel(KQueueDomainSocketChannel.class);
                } else if (Epoll.isAvailable()) {
                    clientBootstrap.channel(EpollDomainSocketChannel.class);
                } else {
                    throw new IllegalStateException(
                            "Unable to use unix domain sockets - both Epoll and KQueue are not available");
                }
            } else {
                if (configuration.isNativeTransport()) {
                    if (KQueue.isAvailable()) {
                        clientBootstrap.channel(KQueueSocketChannel.class);
                    } else if (Epoll.isAvailable()) {
                        clientBootstrap.channel(EpollSocketChannel.class);
                    } else {
                        throw new IllegalStateException(
                                "Unable to use native transport - both Epoll and KQueue are not available");
                    }
                } else {
                    clientBootstrap.channel(NioSocketChannel.class);
                }
            }
            clientBootstrap.group(getWorkerGroup());
            if (configuration.getUnixDomainSocketPath() == null) {
                clientBootstrap.option(ChannelOption.SO_KEEPALIVE, configuration.isKeepAlive());
                clientBootstrap.option(ChannelOption.TCP_NODELAY, configuration.isTcpNoDelay());
                clientBootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
            }
            clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());

            //TODO need to check it later;
            // set any additional netty options
            /*
            if (configuration.getOptions() != null) {
                for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                    clientBootstrap.setOption(entry.getKey(), entry.getValue());
                }
            }*/

            // set the pipeline factory, which creates the pipeline for each newly created channels
            clientBootstrap.handler(pipelineFactory);
            SocketAddress socketAddress;
            if (configuration.getUnixDomainSocketPath() != null) {
                Path udsPath = Path.of(configuration.getUnixDomainSocketPath()).toAbsolutePath();
                LOG.debug("Creating new TCP client bootstrap connecting to {} with options {}",
                        udsPath, clientBootstrap);
                socketAddress = new DomainSocketAddress(udsPath.toFile());
            } else {
                LOG.debug("Creating new TCP client bootstrap connecting to {}:{} with options: {}",
                        configuration.getHost(), configuration.getPort(), clientBootstrap);
                socketAddress = new InetSocketAddress(configuration.getHost(), configuration.getPort());
            }
            answer = clientBootstrap.connect(socketAddress);
            LOG.debug("TCP client bootstrap created");
            return answer;
        } else {
            // its okay to create a new bootstrap for each new channel
            Bootstrap connectionlessClientBootstrap = new Bootstrap();
            if (configuration.isNativeTransport()) {
                connectionlessClientBootstrap.channel(EpollDatagramChannel.class);
            } else {
                connectionlessClientBootstrap.channel(NioDatagramChannel.class);
            }
            connectionlessClientBootstrap.group(getWorkerGroup());
            connectionlessClientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());
            connectionlessClientBootstrap.option(ChannelOption.SO_BROADCAST, configuration.isBroadcast());
            connectionlessClientBootstrap.option(ChannelOption.SO_SNDBUF, configuration.getSendBufferSize());
            connectionlessClientBootstrap.option(ChannelOption.SO_RCVBUF, configuration.getReceiveBufferSize());

            //TODO need to check it later
            // set any additional netty options
            /*
            if (configuration.getOptions() != null) {
                for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                    connectionlessClientBootstrap.setOption(entry.getKey(), entry.getValue());
                }
            }*/

            // set the pipeline factory, which creates the pipeline for each newly created channels
            connectionlessClientBootstrap.handler(pipelineFactory);

            // if udp connectionless sending is true we don't do a connect.
            // we just send on the channel created with bind which means
            // really fire and forget. You wont get an PortUnreachableException
            // if no one is listen on the port
            if (!configuration.isUdpConnectionlessSending()) {
                answer = connectionlessClientBootstrap
                        .connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            } else {
                // bind and store channel so we can close it when stopping
                answer = connectionlessClientBootstrap.bind(new InetSocketAddress(0)).sync();
                Channel channel = answer.channel();
                allChannels.add(channel);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Created new UDP client bootstrap connecting to {}:{} with options: {}",
                        configuration.getHost(), configuration.getPort(), connectionlessClientBootstrap);
            }
            return answer;
        }
    }

    protected void notifyChannelOpen(ChannelFuture channelFuture) throws Exception {
        // blocking for channel to be done
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel open finished with {}", channelFuture);
        }

        if (channelFuture.isSuccess()) {
            Channel answer = channelFuture.channel();
            // to keep track of all channels in use
            allChannels.add(answer);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating connector to address: {}", configuration.getAddress());
            }
        }
    }

    protected void releaseChannel(ChannelFuture channelFuture) {
        Channel channel = channelFuture.channel();
        try {
            // Only put the connected channel back to the pool
            if (channel.isActive()) {
                LOG.trace("Putting channel back to pool {}", channel);
                pool.returnObject(channelFuture);
            } else {
                // and if it's not active then invalidate it
                LOG.trace("Invalidating channel from pool {}", channel);
                pool.invalidateObject(channelFuture);
            }
        } catch (Exception e) {
            LOG.warn("Error returning channel to pool {}. This exception will be ignored.", channel, e);
        }
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public ChannelGroup getAllChannels() {
        return allChannels;
    }

    /**
     * Callback that ensures the channel is returned to the pool when we are done.
     */
    private final class NettyProducerCallback implements AsyncCallback {

        private final ChannelFuture channelFuture;
        private final AsyncCallback callback;

        private NettyProducerCallback(ChannelFuture channelFuture, AsyncCallback callback) {
            this.channelFuture = channelFuture;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            // put back in pool
            try {
                releaseChannel(channelFuture);
            } finally {
                // ensure we call the delegated callback
                callback.done(doneSync);
            }
        }
    }

    /**
     * Object factory to create {@link Channel} used by the pool.
     */
    private final class NettyProducerPoolableObjectFactory implements PooledObjectFactory<ChannelFuture> {
        private NettyProducer producer;

        public NettyProducerPoolableObjectFactory(NettyProducer producer) {
            this.producer = producer;
        }

        @Override
        public void activateObject(PooledObject<ChannelFuture> p) throws Exception {
            ChannelFuture channelFuture = p.getObject();
            LOG.trace("activateObject channel request: {}", channelFuture);

            if (channelFuture.isSuccess() && producer.getConfiguration().getRequestTimeout() > 0) {
                LOG.trace("Reset the request timeout as we activate the channel");
                Channel channel = channelFuture.channel();

                ChannelHandler handler = channel.pipeline().get("timeout");
                if (handler == null) {
                    ChannelHandler timeout
                            = new ReadTimeoutHandler(producer.getConfiguration().getRequestTimeout(), TimeUnit.MILLISECONDS);
                    channel.pipeline().addBefore("handler", "timeout", timeout);
                }
            }
        }

        @Override
        public void destroyObject(PooledObject<ChannelFuture> p) throws Exception {
            ChannelFuture channelFuture = p.getObject();
            LOG.trace("Destroying channel request: {}", channelFuture);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Channel channel = future.channel();
                    if (channel.isOpen()) {
                        NettyHelper.close(channel);
                    }
                    allChannels.remove(channel);
                }
            });
            channelFuture.cancel(false);
        }

        @Override
        public void passivateObject(PooledObject<ChannelFuture> p) throws Exception {
            // noop
            ChannelFuture channelFuture = p.getObject();
            LOG.trace("passivateObject channel request: {}", channelFuture);
        }

        @Override
        public boolean validateObject(PooledObject<ChannelFuture> p) {
            ChannelFuture channelFuture = p.getObject();
            // we need a connecting or connected channel to be valid
            if (!channelFuture.isDone()) {
                LOG.trace("Validating connecting channel request: {} -> {}", channelFuture, true);
                return true;
            }
            if (!channelFuture.isSuccess()) {
                LOG.trace("Validating unsuccessful channel request: {} -> {}", channelFuture, false);
                return false;
            }
            Channel channel = channelFuture.channel();
            boolean answer = channel.isActive();
            LOG.trace("Validating channel: {} -> {}", channel, answer);
            return answer;
        }

        @Override
        public PooledObject<ChannelFuture> makeObject() throws Exception {
            ChannelFuture channelFuture = openConnection().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    notifyChannelOpen(future);
                }
            });
            LOG.trace("Requested channel: {}", channelFuture);
            return new DefaultPooledObject<>(channelFuture);
        }

    }

    /**
     * Listener waiting for connection finished while processing exchange
     */
    private class ChannelConnectedListener implements ChannelFutureListener {
        private final Exchange exchange;
        private final BodyReleaseCallback callback;
        private final Object body;

        ChannelConnectedListener(Exchange exchange, BodyReleaseCallback callback, Object body) {
            this.exchange = exchange;
            this.callback = callback;
            this.body = body;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (!future.isDone() || !future.isSuccess()) {
                ConnectException cause = new ConnectException("Cannot connect to " + configuration.getAddress());
                if (future.cause() != null) {
                    cause.initCause(future.cause());
                }
                exchange.setException(cause);
                callback.done(false);
                releaseChannel(future);
                return;
            }

            try {
                processWithConnectedChannel(exchange, callback, future, body);
            } catch (Exception e) {
                exchange.setException(e);
                callback.done(false);
            }
        }
    }

    /**
     * This class is used to release body in case when some error occurred and body was not handed over to netty
     */
    private static final class BodyReleaseCallback implements AsyncCallback {
        private volatile Object body;
        private final AsyncCallback originalCallback;

        private BodyReleaseCallback(AsyncCallback originalCallback, Object body) {
            this.body = body;
            this.originalCallback = originalCallback;
        }

        public AsyncCallback getOriginalCallback() {
            return originalCallback;
        }

        @Override
        public void done(boolean doneSync) {
            ReferenceCountUtil.release(body);
            originalCallback.done(doneSync);
        }
    }
}
