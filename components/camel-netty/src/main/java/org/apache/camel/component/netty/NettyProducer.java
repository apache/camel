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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.SucceededChannelFuture;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramWorkerPool;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyProducer.class);
    private final ChannelGroup allChannels = new DefaultChannelGroup("NettyProducer");
    private CamelContext context;
    private NettyConfiguration configuration;
    private ChannelFactory channelFactory;
    private DatagramChannelFactory datagramChannelFactory;
    private ClientPipelineFactory pipelineFactory;
    private CamelLogger noReplyLogger;
    private BossPool bossPool;
    private WorkerPool workerPool;
    private ObjectPool<Channel> pool;
   

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

        // release the external resource here and we keep the timer open
        // and then shutdown the thread pools
        if (bossPool != null) {
            bossPool.shutdown();
            bossPool = null;
        }
        if (workerPool != null) {
            if (workerPool instanceof ExternalResourceReleasable) {
                // this will first invoke workerPool#shutdown() internally (e.g. org.jboss.netty.channel.socket.nio.AbstractNioWorkerPool)
                ((ExternalResourceReleasable) workerPool).releaseExternalResources();
            } else {
                workerPool.shutdown();
            }
            workerPool = null;
        }

        if (pool != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping producer with channel pool[active={}, idle={}]", pool.getNumActive(), pool.getNumIdle());
            }
            pool.close();
            pool = null;
        }
        
        if (channelFactory != null) {
            // this will first invoke channelFactory#shutdown() internally (see it's javadoc)
            channelFactory.releaseExternalResources();
            channelFactory = null;
        }
        
        if (datagramChannelFactory != null) {
            // this will first invoke datagramChannelFactory#shutdown() internally (see it's javadoc)
            datagramChannelFactory.releaseExternalResources();
            datagramChannelFactory = null;
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
            ChannelHandler oldHandler = existing.getPipeline().get("timeout");
            ReadTimeoutHandler newHandler = new ReadTimeoutHandler(getEndpoint().getTimer(), timeoutInMs, TimeUnit.MILLISECONDS);
            if (oldHandler == null) {
                existing.getPipeline().addBefore("handler", "timeout", newHandler);
            } else {
                existing.getPipeline().replace(oldHandler, "timeout", newHandler);
            }
        }
        
        // need to declare as final
        final Channel channel = existing;
        final AsyncCallback producerCallback = new NettyProducerCallback(channel, callback);

        // setup state as attachment on the channel, so we can access the state later when needed
        channel.setAttachment(new NettyCamelState(producerCallback, exchange));

        InetSocketAddress remoteAddress = null;
        if (!isTcp()) {
            // Need to specify the remoteAddress for udp connection
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
        return (NettyCamelState) channel.getAttachment();
    }

    /**
     * To remove the {@link NettyCamelState} stored on the channel,
     * when no longer needed
     */
    public void removeState(Channel channel) {
        channel.setAttachment(null);
    }

    protected void setupTCPCommunication() throws Exception {
        if (channelFactory == null) {
            // prefer using explicit configured thread pools
            BossPool bp = configuration.getBossPool();
            WorkerPool wp = configuration.getWorkerPool();

            if (bp == null) {
                // create new pool which we should shutdown when stopping as its not shared
                bossPool = new NettyClientBossPoolBuilder()
                        .withTimer(getEndpoint().getTimer())
                        .withBossCount(configuration.getBossCount())
                        .withName("NettyClientTCPBoss")
                        .build();
                bp = bossPool;
            }
            if (wp == null) {
                // create new pool which we should shutdown when stopping as its not shared
                workerPool = new NettyWorkerPoolBuilder()
                        .withWorkerCount(configuration.getWorkerCount())
                        .withName("NettyClientTCPWorker")
                        .build();
                wp = workerPool;
            }
            channelFactory = new NioClientSocketChannelFactory(bp, wp);
        }
    }

    protected void setupUDPCommunication() throws Exception {
        if (datagramChannelFactory == null) {
            int count = configuration.getWorkerCount() > 0 ? configuration.getWorkerCount() : NettyHelper.DEFAULT_IO_THREADS;
            workerPool = new NioDatagramWorkerPool(Executors.newCachedThreadPool(), count);
            datagramChannelFactory = new NioDatagramChannelFactory(workerPool);
        }
    }

    protected ChannelFuture openConnection() throws Exception {
        ChannelFuture answer;

        if (isTcp()) {
            // its okay to create a new bootstrap for each new channel
            ClientBootstrap clientBootstrap = new ClientBootstrap(channelFactory);
            clientBootstrap.setOption("keepAlive", configuration.isKeepAlive());
            clientBootstrap.setOption("tcpNoDelay", configuration.isTcpNoDelay());
            clientBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
            clientBootstrap.setOption("connectTimeoutMillis", configuration.getConnectTimeout());

            // set any additional netty options
            if (configuration.getOptions() != null) {
                for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                    clientBootstrap.setOption(entry.getKey(), entry.getValue());
                }
            }

            // set the pipeline factory, which creates the pipeline for each newly created channels
            clientBootstrap.setPipelineFactory(pipelineFactory);
            answer = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created new TCP client bootstrap connecting to {}:{} with options: {}",
                        new Object[]{configuration.getHost(), configuration.getPort(), clientBootstrap.getOptions()});
            }
            return answer;
        } else {
            // its okay to create a new bootstrap for each new channel
            ConnectionlessBootstrap connectionlessClientBootstrap = new ConnectionlessBootstrap(datagramChannelFactory);
            connectionlessClientBootstrap.setOption("child.keepAlive", configuration.isKeepAlive());
            connectionlessClientBootstrap.setOption("child.tcpNoDelay", configuration.isTcpNoDelay());
            connectionlessClientBootstrap.setOption("child.reuseAddress", configuration.isReuseAddress());
            connectionlessClientBootstrap.setOption("child.connectTimeoutMillis", configuration.getConnectTimeout());
            connectionlessClientBootstrap.setOption("child.broadcast", configuration.isBroadcast());
            connectionlessClientBootstrap.setOption("sendBufferSize", configuration.getSendBufferSize());
            connectionlessClientBootstrap.setOption("receiveBufferSize", configuration.getReceiveBufferSize());

            // set any additional netty options
            if (configuration.getOptions() != null) {
                for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                    connectionlessClientBootstrap.setOption(entry.getKey(), entry.getValue());
                }
            }

            // set the pipeline factory, which creates the pipeline for each newly created channels
            connectionlessClientBootstrap.setPipelineFactory(pipelineFactory);
           
            // if udp connectionless sending is true we don't do a connect.
            // we just send on the channel created with bind which means
            // really fire and forget. You wont get an PortUnreachableException
            // if no one is listen on the port
            if (!configuration.isUdpConnectionlessSending()) {
                answer = connectionlessClientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
            } else {
                // bind and store channel so we can close it when stopping
                Channel channel = connectionlessClientBootstrap.bind(new InetSocketAddress(0));
                allChannels.add(channel);
                answer = new SucceededChannelFuture(channel);
            }
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created new UDP client bootstrap connecting to {}:{} with options: {}",
                       new Object[]{configuration.getHost(), configuration.getPort(), connectionlessClientBootstrap.getOptions()});
            }
            return answer;
        }
    }

    protected Channel openChannel(ChannelFuture channelFuture) throws Exception {
        // blocking for channel to be done
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for operation to complete {} for {} millis", channelFuture, configuration.getConnectTimeout());
        }
        // here we need to wait it in other thread
        final CountDownLatch channelLatch = new CountDownLatch(1);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture cf) throws Exception {
                channelLatch.countDown();
            }
        });
         
        try {
            channelLatch.await(configuration.getConnectTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            throw new CamelException("Interrupted while waiting for " + "connection to "
                                     + configuration.getAddress());
        }
        

        if (!channelFuture.isDone() || !channelFuture.isSuccess()) {
            ConnectException cause = new ConnectException("Cannot connect to " + configuration.getAddress());
            if (channelFuture.getCause() != null) {
                cause.initCause(channelFuture.getCause());
            }
            throw cause;
        }
        Channel answer = channelFuture.getChannel();
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

    public ChannelFactory getChannelFactory() {
        return channelFactory;
    }

    public void setChannelFactory(ChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
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
                if (channel.isConnected()) {
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
            boolean answer = false;    
            if (configuration.isUdpConnectionlessSending()) {
                // we don't need check if the channel is connected
                answer = channel.isOpen();
            } else {
                // we need a connected channel to be valid
                answer = channel.isConnected();
            }
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
