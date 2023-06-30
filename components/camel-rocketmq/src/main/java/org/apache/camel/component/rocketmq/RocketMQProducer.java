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

package org.apache.camel.component.rocketmq;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rocketmq.reply.ReplyManager;
import org.apache.camel.component.rocketmq.reply.RocketMQReplyManagerSupport;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQProducer extends DefaultAsyncProducer {

    public static final String GENERATE_MESSAGE_KEY_PREFIX = "camel-rocketmq-";

    private static final Logger LOG = LoggerFactory.getLogger(RocketMQProducer.class);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private DefaultMQProducer mqProducer;

    private ReplyManager replyManager;

    public RocketMQProducer(RocketMQEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public RocketMQEndpoint getEndpoint() {
        return (RocketMQEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            callback.done(true);
            return true;
        }
        try {
            LOG.trace("Exchange Pattern {}", exchange.getPattern());
            if (exchange.getPattern().isOutCapable()) {
                return processInOut(exchange, callback);
            } else {
                return processInOnly(exchange, callback);
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean processInOut(final Exchange exchange, final AsyncCallback callback)
            throws RemotingException, MQClientException, InterruptedException, NoTypeConversionAvailableException {
        org.apache.camel.Message in = exchange.getIn();
        Message message = new Message();
        message.setTopic(in.getHeader(RocketMQConstants.OVERRIDE_TOPIC_NAME, () -> getEndpoint().getTopicName(), String.class));
        message.setTags(in.getHeader(RocketMQConstants.OVERRIDE_TAG, () -> getEndpoint().getSendTag(), String.class));
        message.setBody(exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, in.getBody()));
        message.setKeys(in.getHeader(RocketMQConstants.OVERRIDE_MESSAGE_KEY, "", String.class));
        initReplyManager();
        String generateKey = GENERATE_MESSAGE_KEY_PREFIX + getEndpoint().getCamelContext().getUuidGenerator().generateUuid();
        message.setKeys(Arrays.asList(Optional.ofNullable(message.getKeys()).orElse(""), generateKey));
        LOG.debug("RocketMQ Producer sending {}", message);
        mqProducer.send(message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                if (!SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                    exchange.setException(new SendFailedException(sendResult.toString()));
                    callback.done(false);
                    return;
                }
                if (replyManager == null) {
                    LOG.warn("replyToTopic not set! Will not wait for reply.");
                    callback.done(false);
                    return;
                }
                replyManager.registerReply(replyManager, exchange, callback, generateKey,
                        getEndpoint().getRequestTimeoutMillis());
            }

            @Override
            public void onException(Throwable e) {
                try {
                    replyManager.cancelMessageKey(generateKey);
                    exchange.setException(e);
                } finally {
                    callback.done(false);
                }
            }
        });
        return false;
    }

    protected void initReplyManager() {
        if (!started.get()) {
            synchronized (this) {
                if (started.get()) {
                    return;
                }
                LOG.debug("Starting reply manager");
                ClassLoader current = Thread.currentThread().getContextClassLoader();
                ClassLoader ac = getEndpoint().getCamelContext().getApplicationContextClassLoader();
                try {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(ac);
                    }
                    if (getEndpoint().getReplyToTopic() != null) {
                        replyManager = createReplyManager();
                        LOG.debug("Using RocketMQReplyManager: {} to process replies from topic {}", replyManager,
                                getEndpoint().getReplyToTopic());
                    }
                } catch (Exception e) {
                    throw new FailedToCreateProducerException(getEndpoint(), e);
                } finally {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(current);
                    }
                }
                started.set(true);
            }
        }
    }

    protected void unInitReplyManager() {
        try {
            if (replyManager != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stopping RocketMQReplyManager: {} from processing replies from : {}", replyManager,
                            getEndpoint().getReplyToTopic());
                }
                ServiceHelper.stopService(replyManager);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            started.set(false);
        }
    }

    private ReplyManager createReplyManager() {
        RocketMQReplyManagerSupport replyManager = new RocketMQReplyManagerSupport(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());
        String name = "RocketMQReplyManagerTimeoutChecker[" + getEndpoint().getTopicName() + "]";
        ScheduledExecutorService scheduledExecutorService
                = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(scheduledExecutorService);
        LOG.debug("Starting ReplyManager: {}", name);
        ServiceHelper.startService(replyManager);
        return replyManager;
    }

    protected boolean processInOnly(Exchange exchange, AsyncCallback callback)
            throws NoTypeConversionAvailableException, InterruptedException, RemotingException, MQClientException {
        org.apache.camel.Message in = exchange.getIn();
        Message message = new Message();
        message.setTopic(in.getHeader(RocketMQConstants.OVERRIDE_TOPIC_NAME, () -> getEndpoint().getTopicName(), String.class));
        message.setTags(in.getHeader(RocketMQConstants.OVERRIDE_TAG, () -> getEndpoint().getSendTag(), String.class));
        message.setBody(exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, in.getBody()));
        message.setKeys(in.getHeader(RocketMQConstants.OVERRIDE_MESSAGE_KEY, "", String.class));
        LOG.debug("RocketMQ Producer sending {}", message);
        boolean waitForSendResult = getEndpoint().isWaitForSendResult();
        mqProducer.send(message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                if (!SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                    exchange.setException(new SendFailedException(sendResult.toString()));
                }
                callback.done(!waitForSendResult);
            }

            @Override
            public void onException(Throwable e) {
                exchange.setException(e);
                callback.done(!waitForSendResult);
            }
        });
        // return false to wait send callback
        return !waitForSendResult;
    }

    @Override
    protected void doStart() throws Exception {
        this.mqProducer = new DefaultMQProducer(
                null, getEndpoint().getProducerGroup(),
                RocketMQAclUtils.getAclRPCHook(getEndpoint().getAccessKey(), getEndpoint().getSecretKey()));
        this.mqProducer.setNamesrvAddr(getEndpoint().getNamesrvAddr());
        this.mqProducer.start();
    }

    @Override
    protected void doStop() {
        unInitReplyManager();
        this.mqProducer.shutdown();
        this.mqProducer = null;
    }
}
