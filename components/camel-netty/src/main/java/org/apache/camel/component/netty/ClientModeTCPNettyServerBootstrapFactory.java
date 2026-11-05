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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class ClientModeTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(ClientModeTCPNettyServerBootstrapFactory.class);

    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelInitializer<Channel> pipelineFactory;
    private Bootstrap clientBootstrap;
    private EventLoopGroup workerGroup;
    // guards the fields below: the lifecycle thread and the event loops (reconnect) use them
    private final Lock reconnectLock = new ReentrantLock();
    private Channel channel;
    private ChannelFuture pendingConnect;
    private ScheduledFuture<?> reconnectTask;
    private boolean stopping;
    private int generation;

    public ClientModeTCPNettyServerBootstrapFactory() {
    }

    @Override
    public void init(
            CamelContext camelContext, NettyServerBootstrapConfiguration configuration,
            ChannelInitializer<Channel> pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    @Override
    public void init(
            ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration,
            ChannelInitializer<Channel> pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    @Override
    public void addChannel(Channel channel) {
        // we don't need to track the channel in client mode
    }

    @Override
    public void removeChannel(Channel channel) {
        // we don't need to track the channel in client mode
    }

    @Override
    public void addConsumer(NettyConsumer consumer) {
        // does not allow sharing
    }

    @Override
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
        // prefer using explicit configured thread pools

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

        clientBootstrap = new Bootstrap();
        if (configuration.isNativeTransport()) {
            clientBootstrap.channel(EpollSocketChannel.class);
        } else {
            clientBootstrap.channel(NioSocketChannel.class);
        }
        clientBootstrap.group(wg);
        clientBootstrap.option(ChannelOption.SO_KEEPALIVE, configuration.isKeepAlive());
        clientBootstrap.option(ChannelOption.TCP_NODELAY, configuration.isTcpNoDelay());
        clientBootstrap.option(ChannelOption.SO_REUSEADDR, configuration.isReuseAddress());
        clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnectTimeout());

        LOG.debug("Created ClientBootstrap {}", clientBootstrap);
        clientBootstrap.handler(pipelineFactory);
        reconnectLock.lock();
        try {
            // a new start: callbacks of an earlier start do nothing
            stopping = false;
            generation++;
        } finally {
            reconnectLock.unlock();
        }
        ChannelFuture channelFuture
                = clientBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));

        LOG.debug("Created new TCP client bootstrap connecting to {}:{} with options: {}",
                configuration.getHost(), configuration.getPort(), clientBootstrap);

        LOG.info("ClientModeServerBootstrap binding to {}:{}", configuration.getHost(), configuration.getPort());
        Channel answer = openChannel(channelFuture);
        reconnectLock.lock();
        try {
            if (answer != null && channel == null) {
                channel = answer;
            }
        } finally {
            reconnectLock.unlock();
        }
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ClientModeServerBootstrap unbinding from {}:{}", configuration.getHost(), configuration.getPort());

        // the worker group may be shared (workerGroup option), so the channel, a connect in progress and a
        // scheduled reconnect must be closed and cancelled here and not left to the shutdown of the group
        List<Channel> channels = new ArrayList<>(2);
        reconnectLock.lock();
        try {
            stopping = true;
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
            if (pendingConnect != null) {
                channels.add(pendingConnect.channel());
                pendingConnect = null;
            }
            if (channel != null) {
                channels.add(channel);
                channel = null;
            }
        } finally {
            reconnectLock.unlock();
        }
        for (Channel ch : channels) {
            try {
                ChannelFuture closeFuture = ch.close();
                // cannot wait on the event loop of the channel (a route stopped from its own processing)
                if (!ch.eventLoop().inEventLoop()) {
                    closeFuture.awaitUninterruptibly(configuration.getShutdownTimeout());
                }
            } catch (RuntimeException e) {
                // a channel that never got an event loop (its creation or registration failed) has nothing to close
                LOG.debug("Cannot close channel {} due to {}. This exception is ignored.", ch, e.getMessage(), e);
            }
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

    /**
     * Connects again if the channel is not active and the consumer is not stopping. Does not wait for the connect (it
     * may run on an event loop): the outcome is handled by a listener.
     */
    protected void doReconnectIfNeeded() throws Exception {
        doReconnectIfNeeded(currentGeneration());
    }

    private void doReconnectIfNeeded(int gen) {
        // resolve the address before taking the lock (may do a DNS lookup)
        InetSocketAddress address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        reconnectLock.lock();
        try {
            // a stopped or restarted consumer, or a connect already in progress, does not connect again
            if (stopping || gen != generation || pendingConnect != null || (channel != null && channel.isActive())) {
                return;
            }
            LOG.debug("ClientModeServerBootstrap re-connect to {}:{}", configuration.getHost(), configuration.getPort());
            final ChannelFuture connectFuture = clientBootstrap.connect(address);
            pendingConnect = connectFuture;
            connectFuture.addListener((ChannelFutureListener) f -> onReconnectComplete(f, gen));
        } finally {
            reconnectLock.unlock();
        }
    }

    private void onReconnectComplete(ChannelFuture connectFuture, int gen) {
        reconnectLock.lock();
        try {
            if (pendingConnect == connectFuture) {
                pendingConnect = null;
            }
            if (stopping || gen != generation) {
                // the consumer was stopped or restarted while connecting
                connectFuture.channel().close();
                return;
            }
            if (connectFuture.isSuccess()) {
                channel = connectFuture.channel();
                LOG.debug("ClientModeServerBootstrap re-connected to {}", configuration.getAddress());
                closeFutureReconnect(channel, gen);
            } else {
                LOG.debug("Re-connect to {} failed. Will attempt again in {} millis.", configuration.getAddress(),
                        configuration.getReconnectInterval(), connectFuture.cause());
                scheduleReconnect(eventLoopOf(connectFuture.channel()), gen);
            }
        } finally {
            reconnectLock.unlock();
        }
    }

    protected Channel openChannel(final ChannelFuture channelFuture) throws Exception {
        // blocking for channel to be done
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for operation to complete {} for {} millis", channelFuture, configuration.getConnectTimeout());
        }

        // wait for the channel to be open (see io.netty.channel.ChannelFuture javadoc for example/recommendation)
        channelFuture.awaitUninterruptibly();

        int gen = currentGeneration();
        if (!channelFuture.isDone() || !channelFuture.isSuccess()) {
            //check if reconnect is enabled and schedule a reconnect, if from handler then don't schedule a reconnect
            if (configuration.isReconnect()) {
                scheduleReconnect(eventLoopOf(channelFuture.channel()), gen);
                return null;
            } else {
                ConnectException cause = new ConnectException("Cannot connect to " + configuration.getAddress());
                if (channelFuture.cause() != null) {
                    cause.initCause(channelFuture.cause());
                }
                throw cause;
            }
        }
        Channel answer = channelFuture.channel();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: {}", configuration.getAddress());
        }

        closeFutureReconnect(answer, gen);
        return answer;
    }

    private void closeFutureReconnect(Channel answer, int gen) {
        // schedule a reconnect to happen when the channel closes
        if (configuration.isReconnect()) {
            answer.closeFuture().addListener((ChannelFutureListener) f -> scheduleReconnect(answer.eventLoop(), gen));
        }
    }

    private void scheduleReconnect(final EventLoop loop, final int gen) {
        reconnectLock.lock();
        try {
            if (stopping || gen != generation) {
                return;
            }
            reconnectTask = loop.schedule(() -> {
                try {
                    LOG.trace("Re-connecting to {} if needed", configuration.getAddress());
                    doReconnectIfNeeded(gen);
                } catch (Exception e) {
                    LOG.warn("Error during re-connect to {}. Will attempt again in {} millis. This exception is ignored.",
                            configuration.getAddress(), configuration.getReconnectInterval(),
                            e);
                    scheduleReconnect(loop, gen);
                }
            }, configuration.getReconnectInterval(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            LOG.debug("Cannot schedule a re-connect to {} as the event loop is shutting down", configuration.getAddress());
        } finally {
            reconnectLock.unlock();
        }
    }

    private EventLoop eventLoopOf(Channel ch) {
        // a channel whose registration failed has no event loop
        return ch.isRegistered() ? ch.eventLoop() : clientBootstrap.config().group().next();
    }

    private int currentGeneration() {
        reconnectLock.lock();
        try {
            return generation;
        } finally {
            reconnectLock.unlock();
        }
    }

}
