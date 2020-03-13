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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rabbitmq.pool.PoolableChannelFactory;
import org.apache.camel.component.rabbitmq.reply.ReplyManager;
import org.apache.camel.component.rabbitmq.reply.TemporaryQueueReplyManager;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQProducer.class);

    private static final String GENERATED_CORRELATION_ID_PREFIX = "Camel-";

    private Connection conn;
    private ObjectPool<Channel> channelPool;
    private ExecutorService executorService;
    private int closeTimeout = 30 * 1000;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ReplyManager replyManager;

    public RabbitMQProducer(RabbitMQEndpoint endpoint) throws IOException {
        super(endpoint);
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint)super.getEndpoint();
    }

    /**
     * Channel callback (similar to Spring JDBC ConnectionCallback)
     */
    private interface ChannelCallback<T> {
        T doWithChannel(Channel channel) throws Exception;
    }

    /**
     * Do something with a pooled channel (similar to Spring JDBC
     * TransactionTemplate#execute)
     */
    private <T> T execute(ChannelCallback<T> callback) throws Exception {
        Channel channel;
        try {
            channel = channelPool.borrowObject();
        } catch (IllegalStateException e) {
            // Since this method is not synchronized its possible the
            // channelPool has been cleared by another thread
            checkConnectionAndChannelPool();
            channel = channelPool.borrowObject();
        }
        if (!channel.isOpen()) {
            LOG.warn("Got a closed channel from the pool. Invalidating and borrowing a new one from the pool.");
            channelPool.invalidateObject(channel);
            // Reconnect if another thread hasn't yet
            checkConnectionAndChannelPool();
            attemptDeclaration();
            channel = channelPool.borrowObject();
        }
        try {
            return callback.doWithChannel(channel);
        } finally {
            channelPool.returnObject(channel);
        }
    }

    /**
     * Open connection and initialize channel pool
     * 
     * @throws Exception
     */
    private synchronized void openConnectionAndChannelPool() throws Exception {
        LOG.trace("Creating connection...");
        this.conn = getEndpoint().connect(executorService);
        LOG.debug("Created connection: {}", conn);

        LOG.trace("Creating channel pool...");
        channelPool = new GenericObjectPool<>(new PoolableChannelFactory(this.conn), getEndpoint().getChannelPoolMaxSize(), GenericObjectPool.WHEN_EXHAUSTED_BLOCK,
                                              getEndpoint().getChannelPoolMaxWait());
        attemptDeclaration();
    }

    private synchronized void attemptDeclaration() throws Exception {
        if (getEndpoint().isDeclare()) {
            execute(new ChannelCallback<Void>() {
                @Override
                public Void doWithChannel(Channel channel) throws Exception {
                    getEndpoint().declareExchangeAndQueue(channel);
                    return null;
                }
            });
        }
    }

    /**
     * This will reconnect only if the connection is closed.
     * 
     * @throws Exception
     */
    private synchronized void checkConnectionAndChannelPool() throws Exception {
        if (this.conn == null || !this.conn.isOpen()) {
            LOG.info("Reconnecting to RabbitMQ");
            try {
                closeConnectionAndChannel();
            } catch (Exception e) {
                // no op
            }
            openConnectionAndChannelPool();
        }
    }

    @Override
    protected void doStart() throws Exception {
        this.executorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "CamelRabbitMQProducer[" + getEndpoint().getQueue() + "]");
        try {
            openConnectionAndChannelPool();
        } catch (IOException e) {
            LOG.warn("Failed to create connection. It will attempt to connect again when publishing a message.", e);
        }
    }

    /**
     * If needed, close Connection and Channel
     * 
     * @throws IOException
     */
    private synchronized void closeConnectionAndChannel() throws IOException {
        if (channelPool != null) {
            try {
                channelPool.close();
                channelPool = null;
            } catch (Exception e) {
                throw new IOException("Error closing channelPool", e);
            }
        }
        if (conn != null) {
            LOG.debug("Closing connection: {} with timeout: {} ms.", conn, closeTimeout);
            conn.close(closeTimeout);
            conn = null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        unInitReplyManager();
        closeConnectionAndChannel();
        if (executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // deny processing if we are not started
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            // we cannot process so invoke callback
            callback.done(true);
            return true;
        }

        try {
            if (exchange.getPattern().isOutCapable()) {
                // in out requires a bit more work than in only
                return processInOut(exchange, callback);
            } else {
                // in only
                return processInOnly(exchange, callback);
            }
        } catch (Throwable e) {
            // must catch exception to ensure callback is invoked as expected
            // to let Camel error handling deal with this
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean processInOut(final Exchange exchange, final AsyncCallback callback) throws Exception {
        final org.apache.camel.Message in = exchange.getIn();

        initReplyManager();

        // the request timeout can be overruled by a header otherwise the
        // endpoint configured value is used
        final long timeout = exchange.getIn().getHeader(RabbitMQConstants.REQUEST_TIMEOUT, getEndpoint().getRequestTimeout(), long.class);

        final String originalCorrelationId = in.getHeader(RabbitMQConstants.CORRELATIONID, String.class);

        // we append the 'Camel-' prefix to know it was generated by us
        String correlationId = GENERATED_CORRELATION_ID_PREFIX + getEndpoint().getCamelContext().getUuidGenerator().generateUuid();
        in.setHeader(RabbitMQConstants.CORRELATIONID, correlationId);

        in.setHeader(RabbitMQConstants.REPLY_TO, replyManager.getReplyTo());

        String exchangeName = (String)exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME);
        // If it is BridgeEndpoint we should ignore the message header of
        // EXCHANGE_OVERRIDE_NAME
        if (exchangeName == null || getEndpoint().isBridgeEndpoint()) {
            exchangeName = getEndpoint().getExchangeName();
        } else {
            LOG.debug("Overriding header: {} detected sending message to exchange: {}", RabbitMQConstants.EXCHANGE_OVERRIDE_NAME, exchangeName);
        }

        String key = in.getHeader(RabbitMQConstants.ROUTING_KEY, String.class);
        // we just need to make sure RoutingKey option take effect if it is not
        // BridgeEndpoint
        if (key == null || getEndpoint().isBridgeEndpoint()) {
            key = getEndpoint().getRoutingKey() == null ? "" : getEndpoint().getRoutingKey();
        }
        if (ObjectHelper.isEmpty(key) && ObjectHelper.isEmpty(exchangeName)) {
            throw new IllegalArgumentException("ExchangeName and RoutingKey is not provided in the endpoint: " + getEndpoint());
        }
        LOG.debug("Registering reply for {}", correlationId);

        replyManager.registerReply(replyManager, exchange, callback, originalCorrelationId, correlationId, timeout);
        try {
            basicPublish(exchange, exchangeName, key);
        } catch (Exception e) {
            replyManager.cancelCorrelationId(correlationId);
            exchange.setException(e);
            return true;
        }
        // continue routing asynchronously (reply will be processed async when
        // its received)
        return false;
    }

    private boolean processInOnly(Exchange exchange, AsyncCallback callback) throws Exception {
        String exchangeName = (String)exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME);
        // If it is BridgeEndpoint we should ignore the message header of
        // EXCHANGE_OVERRIDE_NAME
        if (exchangeName == null || getEndpoint().isBridgeEndpoint()) {
            exchangeName = getEndpoint().getExchangeName();
        } else {
            LOG.debug("Overriding header: {} detected sending message to exchange: {}", RabbitMQConstants.EXCHANGE_OVERRIDE_NAME, exchangeName);
        }

        String key = exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY, String.class);
        // we just need to make sure RoutingKey option take effect if it is not
        // BridgeEndpoint
        if (key == null || getEndpoint().isBridgeEndpoint()) {
            key = getEndpoint().getRoutingKey() == null ? "" : getEndpoint().getRoutingKey();
        }
        if (ObjectHelper.isEmpty(key) && ObjectHelper.isEmpty(exchangeName)) {
            throw new IllegalArgumentException("ExchangeName and RoutingKey is not provided in the endpoint: " + getEndpoint());
        }

        basicPublish(exchange, exchangeName, key);
        callback.done(true);
        return true;
    }

    /**
     * Send a message borrowing a channel from the pool.
     */
    private void basicPublish(final Exchange camelExchange, final String rabbitExchange, final String routingKey) throws Exception {
        if (channelPool == null) {
            // Open connection and channel lazily if another thread hasn't
            checkConnectionAndChannelPool();
        }
        execute(new ChannelCallback<Void>() {
            @Override
            public Void doWithChannel(Channel channel) throws Exception {
                getEndpoint().publishExchangeToChannel(camelExchange, channel, routingKey);
                return null;
            }
        });
    }

    AMQP.BasicProperties.Builder buildProperties(Exchange exchange) {
        return getEndpoint().getMessageConverter().buildProperties(exchange);
    }

    public int getCloseTimeout() {
        return closeTimeout;
    }

    public void setCloseTimeout(int closeTimeout) {
        this.closeTimeout = closeTimeout;
    }

    protected void initReplyManager() {
        if (!started.get()) {
            synchronized (this) {
                if (started.get()) {
                    return;
                }
                LOG.debug("Starting reply manager");
                // must use the classloader from the application context when
                // creating reply manager,
                // as it should inherit the classloader from app context and not
                // the current which may be
                // a different classloader
                ClassLoader current = Thread.currentThread().getContextClassLoader();
                ClassLoader ac = getEndpoint().getCamelContext().getApplicationContextClassLoader();
                try {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(ac);
                    }
                    // validate that replyToType and replyTo is configured
                    // accordingly
                    if (getEndpoint().getReplyToType() != null) {
                        // setting temporary with a fixed replyTo is not
                        // supported
                        if (getEndpoint().getReplyTo() != null && getEndpoint().getReplyToType().equals(ReplyToType.Temporary.name())) {
                            throw new IllegalArgumentException("ReplyToType " + ReplyToType.Temporary + " is not supported when replyTo " + getEndpoint().getReplyTo()
                                                               + " is also configured.");
                        }
                    }

                    if (getEndpoint().getReplyTo() != null) {
                        // specifying reply queues is not currently supported
                        throw new IllegalArgumentException("Specifying replyTo " + getEndpoint().getReplyTo() + " is currently not supported.");
                    } else {
                        replyManager = createReplyManager();
                        LOG.debug("Using RabbitMQReplyManager: {} to process replies from temporary queue", replyManager);
                    }
                } catch (Exception e) {
                    throw new FailedToCreateProducerException(getEndpoint(), e);
                } finally {
                    Thread.currentThread().setContextClassLoader(current);
                }
                started.set(true);
            }
        }
    }

    protected void unInitReplyManager() {
        try {
            if (replyManager != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stopping RabbitMQReplyManager: {} from processing replies from: {}", replyManager,
                              getEndpoint().getReplyTo() != null ? getEndpoint().getReplyTo() : "temporary queue");
                }
                ServiceHelper.stopService(replyManager);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            started.set(false);
        }
    }

    protected ReplyManager createReplyManager() throws Exception {
        // use a temporary queue
        ReplyManager replyManager = new TemporaryQueueReplyManager(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());

        String name = "RabbitMQReplyManagerTimeoutChecker[" + getEndpoint().getExchangeName() + "]";
        ScheduledExecutorService replyManagerExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerExecutorService);
        LOG.debug("Staring ReplyManager: {}", name);
        ServiceHelper.startService(replyManager);

        return replyManager;
    }
}
