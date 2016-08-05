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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Suspendable;
import org.apache.camel.support.ServiceSupport;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.component.netty.NettyServerBootstrapFactory} which is used by a single consumer (not shared).
 */
public class ClientModeTCPNettyServerBootstrapFactory extends ServiceSupport implements NettyServerBootstrapFactory, Suspendable {

    protected static final Logger LOG = LoggerFactory.getLogger(ClientModeTCPNettyServerBootstrapFactory.class);
    private CamelContext camelContext;
    private ThreadFactory threadFactory;
    private NettyServerBootstrapConfiguration configuration;
    private ChannelPipelineFactory pipelineFactory;
    private ChannelFactory channelFactory;
    private ClientBootstrap serverBootstrap;
    private Channel channel;
    private BossPool bossPool;
    private WorkerPool workerPool;
   

    public void init(CamelContext camelContext, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    public void init(ThreadFactory threadFactory, NettyServerBootstrapConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.pipelineFactory = pipelineFactory;
    }

    public void addChannel(Channel channel) {
        // we don't need to track the channel in client mode
    }

    public void removeChannel(Channel channel) {
        // we don't need to track the channel in client mode
    }

    public void addConsumer(NettyConsumer consumer) {
        // does not allow sharing
    }

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

    @Override
    protected void doResume() throws Exception {
        ChannelFuture connectFuture = serverBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        channel = openChannel(connectFuture);
    }

    @Override
    protected void doSuspend() throws Exception {
        if (channel != null) {
            LOG.debug("ServerBootstrap disconnecting from {}:{}", configuration.getHost(), configuration.getPort());
            channel.disconnect().sync();
            channel = null;
        }
    }

    protected void startServerBootstrap() {
        // prefer using explicit configured thread pools
        BossPool bp = configuration.getBossPool();
        WorkerPool wp = configuration.getWorkerPool();

        if (bp == null) {
            // create new pool which we should shutdown when stopping as its not shared
            bossPool = new NettyClientBossPoolBuilder()
                    .withTimer(new HashedWheelTimer())
                    .withBossCount(configuration.getBossCount())
                    .withName("NettyClientTCPBoss")
                    .build();
            bp = bossPool;
        }
        if (wp == null) {
            // create new pool which we should shutdown when stopping as its not shared
            workerPool = new NettyWorkerPoolBuilder()
                    .withWorkerCount(configuration.getWorkerCount())
                    .withName("NettyServerTCPWorker")
                    .build();
            wp = workerPool;
        }

        channelFactory = new NioClientSocketChannelFactory(bp, wp);

        serverBootstrap = new ClientBootstrap(channelFactory);
        serverBootstrap.setOption("keepAlive", configuration.isKeepAlive());
        serverBootstrap.setOption("tcpNoDelay", configuration.isTcpNoDelay());
        serverBootstrap.setOption("reuseAddress", configuration.isReuseAddress());
        serverBootstrap.setOption("connectTimeoutMillis", configuration.getConnectTimeout());
        if (configuration.getBacklog() > 0) {
            serverBootstrap.setOption("backlog", configuration.getBacklog());
        }

        // set any additional netty options
        if (configuration.getOptions() != null) {
            for (Map.Entry<String, Object> entry : configuration.getOptions().entrySet()) {
                serverBootstrap.setOption(entry.getKey(), entry.getValue());
            }
        }

        LOG.debug("Created ServerBootstrap {} with options: {}", serverBootstrap, serverBootstrap.getOptions());

        // set the pipeline factory, which creates the pipeline for each newly created channels
        serverBootstrap.setPipelineFactory(pipelineFactory);

        LOG.info("ServerBootstrap connecting to {}:{}", configuration.getHost(), configuration.getPort());
        ChannelFuture connectFuture = serverBootstrap.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()));
        try {
            channel = openChannel(connectFuture);
        } catch (Exception e) {
            e.printStackTrace();
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

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: {}", configuration.getAddress());
        }
        return answer;
    }

    protected void stopServerBootstrap() {
        // close all channels
        LOG.info("ServerBootstrap disconnecting from {}:{}", configuration.getHost(), configuration.getPort());

        // close server external resources
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
            channelFactory = null;
        }

        // and then shutdown the thread pools
        if (bossPool != null) {
            bossPool.shutdown();
            bossPool = null;
        }
        if (workerPool != null) {
            workerPool.shutdown();
            workerPool = null;
        }
    }

}
