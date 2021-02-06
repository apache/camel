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
package org.apache.camel.component.nats;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.nats.client.Connection;
import io.nats.client.Connection.Status;
import io.nats.client.Message;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(NatsProducer.class);

    private final ExecutorServiceManager executorServiceManager;

    private ScheduledExecutorService scheduler;

    private Connection connection;

    public NatsProducer(NatsEndpoint endpoint) {
        super(endpoint);
        this.executorServiceManager = endpoint.getCamelContext().getExecutorServiceManager();
    }

    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        NatsConfiguration config = getEndpoint().getConfiguration();
        byte[] body = exchange.getIn().getBody(byte[].class);
        if (body == null) {
            // fallback to use string
            try {
                body = exchange.getIn().getMandatoryBody(String.class).getBytes();
            } catch (InvalidPayloadException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        if (exchange.getPattern().isOutCapable()) {
            LOG.debug("Requesting to topic: {}", config.getTopic());

            CompletableFuture<Message> requestFuture = connection.request(config.getTopic(), body);
            CompletableFuture timeoutFuture = this.failAfter(exchange, Duration.ofMillis(config.getRequestTimeout()));
            CompletableFuture.anyOf(requestFuture, timeoutFuture).whenComplete((message, e) -> {
                if (e == null) {
                    Message msg = (Message) message;
                    exchange.getMessage().setBody(msg.getData());
                    exchange.getMessage().setHeader(NatsConstants.NATS_REPLY_TO, msg.getReplyTo());
                    exchange.getMessage().setHeader(NatsConstants.NATS_SID, msg.getSID());
                    exchange.getMessage().setHeader(NatsConstants.NATS_SUBJECT, msg.getSubject());
                    exchange.getMessage().setHeader(NatsConstants.NATS_QUEUE_NAME, msg.getSubscription().getQueueName());
                    exchange.getMessage().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                } else {
                    exchange.setException(e.getCause());
                }
                callback.done(false);
                if (!requestFuture.isDone()) {
                    requestFuture.cancel(true);
                }
                if (!timeoutFuture.isDone()) {
                    timeoutFuture.cancel(true);
                }
            });
            return false;
        } else {
            LOG.debug("Publishing to topic: {}", config.getTopic());

            if (ObjectHelper.isNotEmpty(config.getReplySubject())) {
                String replySubject = config.getReplySubject();
                connection.publish(config.getTopic(), replySubject, body);
            } else {
                connection.publish(config.getTopic(), body);
            }
            callback.done(true);
            return true;
        }
    }

    private <T> CompletableFuture<T> failAfter(Exchange exchange, Duration duration) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(() -> {
            final ExchangeTimedOutException ex = new ExchangeTimedOutException(exchange, duration.toMillis());
            return future.completeExceptionally(ex);
        }, duration.toNanos(), TimeUnit.NANOSECONDS);
        return future;
    }

    @Override
    protected void doStart() throws Exception {
        // try to lookup a pool first based on profile
        ThreadPoolProfile profile
                = this.executorServiceManager.getThreadPoolProfile(NatsConstants.NATS_REQUEST_TIMEOUT_THREAD_PROFILE_NAME);
        if (profile == null) {
            profile = this.executorServiceManager.getDefaultThreadPoolProfile();
        }
        this.scheduler
                = this.executorServiceManager.newScheduledThreadPool(this,
                        NatsConstants.NATS_REQUEST_TIMEOUT_THREAD_PROFILE_NAME, profile);
        super.doStart();
        LOG.debug("Starting Nats Producer");

        LOG.debug("Getting Nats Connection");
        connection = getEndpoint().getConfiguration().getConnection() != null
                ? getEndpoint().getConfiguration().getConnection() : getEndpoint().getConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (this.scheduler != null) {
            this.executorServiceManager.shutdownNow(this.scheduler);
        }
        LOG.debug("Stopping Nats Producer");
        if (ObjectHelper.isEmpty(getEndpoint().getConfiguration().getConnection())) {
            LOG.debug("Closing Nats Connection");
            if (connection != null && !connection.getStatus().equals(Status.CLOSED)) {
                if (getEndpoint().getConfiguration().isFlushConnection()) {
                    LOG.debug("Flushing Nats Connection");
                    connection.flush(Duration.ofMillis(getEndpoint().getConfiguration().getFlushTimeout()));
                }
                connection.close();
            }
        }
        super.doStop();
    }

}
