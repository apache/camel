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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyProducer.class);
    private final ChannelGroup allChannels = new DefaultChannelGroup("NettyProducer", ImmediateEventExecutor.INSTANCE);
    private CamelContext context;
    private NettyConfiguration configuration;
    private ClientInitializerFactory pipelineFactory;
    private CamelLogger noReplyLogger;
    private EventLoopGroup workerGroup;
    private ObjectPool<Channel> pool;
    private Map<Channel, NettyCamelState> nettyCamelStatesMap = new ConcurrentHashMap<Channel, NettyCamelState>();

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
        return true;
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
        if (configuration.getWorkerGroup() == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerGroup = new NettyWorkerPoolBuilder().withWorkerCount(configuration.getWorkerCount())
                .withName("NettyClientTCPWorker").build();
        }
        
        if (configuration.isProducerPoolEnabled()) {
            // setup pool where we want an unbounded pool, which allows the pool to shrink on no demand
            GenericObjectPool.Config config = new GenericObjectPool.Config();
            config.maxActive = configuration.getProducerPoolMaxActive();
            config.minIdle = configuration.getProducerPoolMinIdle();
            config.maxIdle = configuration.getProducerPoolMaxIdle();
            // we should test on borrow to ensure the channel is still valid
            config.testOnBorrow = true;
            // only evict channels which are no longer valid
            config.testWhileIdle = true;
            // run eviction every 30th second
            config.timeBetweenEvictionRunsMillis = 30 * 1000L;
            config.minEvictableIdleTimeMillis = configuration.getProducerPoolMinEvictableIdle();
            config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
            pool = new GenericObjectPool<Channel>(new NettyProducerPoolableObjectFactory(), config);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Created NettyProducer pool[maxActive={}, minIdle={}, maxIdle={}, minEvictableIdleTimeMillis={}] -> {}",
                        new Object[]{config.maxActive, config.minIdle, config.maxIdle, config.minEvictableIdleTimeMillis, pool});
            }
        } else {
            pool = new SharedSingletonObjectPool<Channel>(new NettyProducerPoolableObjectFactory());
            if (LOG.isDebugEnabled()) {
                LOG.info("Created NettyProducer shared singleton pool -> {}", pool);
            }
        }

        // setup pipeline factory
        ClientInitializerFactory factory = configuration.getClientInitializerFactory();
        if (factory != null) {
            pipelineFactory = factory.createPipelineFactory(this);
        } else {
            pipelineFactory = new DefaultClientInitializerFactory(this);
        }

        if (!configuration.isLazyChannelCreation()) {
            // ensure the connection can be established when we start up
            Channel channel = pool.borrowObject();
            pool.returnObject(channel);
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping producer at address: {}", configuration.getAddress());
        // close all channels
        LOG.trace("Closing {} channels", allChannels.size());
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();

        // and then shutdown the thread pools
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        if (pool != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping producer with channel pool[active={}, idle={}]", pool.getNumActive(), pool.getNumIdle());
            }
            pool.close();
            pool = null;
        }

        super.doStop();
    }

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
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // set the exchange encoding property
        if (getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(getConfiguration().getCharsetName()));
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Pool[active={}, idle={}]", pool.getNumActive(), pool.getNumIdle());
        }

        // get a channel from the pool
        Channel existing;
        try {
            existing = pool.borrowObject();
            if (existing != null) {
                LOG.trace("Got channel from pool {}", existing);
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // we must have a channel
        if (existing == null) {
            exchange.setException(new CamelExchangeException("Cannot get channel from pool", exchange));
            callback.done(true);
            return true;
        }

        if (exchange.getIn().getHeader(NettyConstants.NETTY_REQUEST_TIMEOUT) != null) {
            long timeoutInMs = exchange.getIn().getHeader(NettyConstants.NETTY_REQUEST_TIMEOUT, Long.class);
            ChannelHandler oldHandler = existing.pipeline().get("timeout");
            ReadTimeoutHandler newHandler = new ReadTimeoutHandler(timeoutInMs, TimeUnit.MILLISECONDS);
            if (oldHandler == null) {
                existing.pipeline().addBefore("handler", "timeout", newHandler);
            } else {
                existing.pipeline().replace(oldHandler, "timeout", newHandler);
            }
        }
        
        // need to declare as final
        final Channel channel = existing;
        final AsyncCallback producerCallback = new NettyProducerCallback(channel, callback);

        // setup state as attachment on the channel, so we can access the state later when needed
        putState(channel, new NettyCamelState(producerCallback, exchange));
        // here we need to setup the remote address information here
        InetSocketAddress remoteAddress = null;
        if (!isTcp()) {
            remoteAddress = new InetSocketAddress(configuration.getHost(), configuration.getPort()); 
        }

        // write body
        NettyHelper.writeBodyAsync(LOG, channel, remoteAddress, body, exchange, new ChannelFutureListener() {
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                LOG.trace("Operation complete {}", channelFuture);
                if (!channelFuture.isSuccess()) {
                    // no success then exit, (any exception has been handled by ClientChannelHandler#exceptionCaught)
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
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Closing channel when complete at address: {}", getEndpoint().getConfiguration().getAddress());
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

        // continue routing asynchronously
        return false;
    }

    /**
     * Gets the object we want to use as the request object for sending to netty.
     *
     * @param exchange the exchange
     * @return the object to use as request
     * @throws Exception is thrown if error getting the request body
     */
    protected Object getRequestBody(Exchange exchange) throws Exception {
        Object body = NettyPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            return null;
        }

        // if textline enabled then covert to a String which must be used for textline
        if (getConfiguration().isTextline()) {
            body = NettyHelper.getTextlineBody(body, exchange, getConfiguration().getDelimiter(), getConfiguration().isAutoAppendDelimiter());
        }

        return body;
    }

    /**
     * To get the {@link NettyCamelState} from the given channel.
     */
    public NettyCamelState getState(Channel channel) {
        return nettyCamelStatesMap.get(channel);
    }

    /**
     * To remove the {@link NettyCamelState} stored on the channel,
     * when no longer needed
     */
    public void removeState(Channel channel) {
        nettyCamelStatesMap.remove(channel);
    }

    /**
     * Put the {@link NettyCamelState} into the map use the given channel as the key
     */
    public void putState(Channel channel, NettyCamelState state) {
        nettyCamelStatesMap.put(channel, state);
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
            clientBootstrap.channel(NioSocketChannel.class);
            clientBootstrap.group(getWorkerGroup());
            clientBootstrap.option(ChannelOption.SO_KEEPALIVE, configuration.isKeepAlive());
            clientBootstrap.option(ChannelOption.TCP_NODELAY, configuration.isTcpNoDelay());
            clientBootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
            clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());

            //TODO need to check it later
            // set any additional netty options
            /*
            if (configuration.getOptions() != null) {
                for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                    clientBootstrap.setOption(entry.getKey(), entry.getValue());
                }
            }*/

            // set the pipeline factory, which creates the pipeline for each newly created channels
            clientBootstrap.handler(pipelineFactory);
            answer = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created new TCP client bootstrap connecting to {}:{} with options: {}",
                        new Object[]{configuration.getHost(), configuration.getPort(), clientBootstrap});
            }
            return answer;
        } else {
            // its okay to create a new bootstrap for each new channel
            Bootstrap connectionlessClientBootstrap = new Bootstrap();
            connectionlessClientBootstrap.channel(NioDatagramChannel.class);
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
                answer = connectionlessClientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            } else {
                // bind and store channel so we can close it when stopping
                answer = connectionlessClientBootstrap.bind(new InetSocketAddress(0)).sync();
                Channel channel = answer.channel();
                allChannels.add(channel);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Created new UDP client bootstrap connecting to {}:{} with options: {}",
                       new Object[]{configuration.getHost(), configuration.getPort(), connectionlessClientBootstrap});
            }
            return answer;
        }
    }

    protected Channel openChannel(ChannelFuture channelFuture) throws Exception {
        // blocking for channel to be done
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for operation to complete {} for {} millis", channelFuture, configuration.getConnectTimeout());
        }

        // wait for the channel to be open (see io.netty.channel.ChannelFuture javadoc for example/recommendation)
        channelFuture.awaitUninterruptibly();

        if (!channelFuture.isDone() || !channelFuture.isSuccess()) {
            ConnectException cause = new ConnectException("Cannot connect to " + configuration.getAddress());
            if (channelFuture.cause() != null) {
                cause.initCause(channelFuture.cause());
            }
            throw cause;
        }
        Channel answer = channelFuture.channel();
        // to keep track of all channels in use
        allChannels.add(answer);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: {}", configuration.getAddress());
        }
        return answer;
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

        private final Channel channel;
        private final AsyncCallback callback;

        private NettyProducerCallback(Channel channel, AsyncCallback callback) {
            this.channel = channel;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            // put back in pool
            try {
                // Only put the connected channel back to the pool
                if (channel.isActive()) {
                    LOG.trace("Putting channel back to pool {}", channel);
                    pool.returnObject(channel);
                }
            } catch (Exception e) {
                LOG.warn("Error returning channel to pool {}. This exception will be ignored.", channel);
            } finally {
                // ensure we call the delegated callback
                callback.done(doneSync);
            }
        }
    }

    /**
     * Object factory to create {@link Channel} used by the pool.
     */
    private final class NettyProducerPoolableObjectFactory implements PoolableObjectFactory<Channel> {

        @Override
        public Channel makeObject() throws Exception {
            ChannelFuture channelFuture = openConnection();
            Channel answer = openChannel(channelFuture);
            LOG.trace("Created channel: {}", answer);
            return answer;
        }

        @Override
        public void destroyObject(Channel channel) throws Exception {
            LOG.trace("Destroying channel: {}", channel);
            if (channel.isOpen()) {
                NettyHelper.close(channel);
            }
            allChannels.remove(channel);
        }

        @Override
        public boolean validateObject(Channel channel) {
            // we need a connected channel to be valid
            boolean answer = channel.isActive();
            LOG.trace("Validating channel: {} -> {}", channel, answer);
            return answer;
        }

        @Override
        public void activateObject(Channel channel) throws Exception {
            // noop
            LOG.trace("activateObject channel: {} -> {}", channel);
        }

        @Override
        public void passivateObject(Channel channel) throws Exception {
            // noop
            LOG.trace("passivateObject channel: {} -> {}", channel);
        }
    }

}
