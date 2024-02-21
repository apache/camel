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
package org.apache.camel.component.springrabbit;

import java.util.Map;

import com.rabbitmq.client.Channel;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

public class EndpointMessageListener implements ChannelAwareMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMessageListener.class);

    private final SpringRabbitMQConsumer consumer;
    private final SpringRabbitMQEndpoint endpoint;
    private final AsyncProcessor processor;
    private RabbitTemplate template;
    private boolean disableReplyTo;
    private boolean async;

    public EndpointMessageListener(SpringRabbitMQConsumer consumer, SpringRabbitMQEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * Sets whether asynchronous routing is enabled.
     * <p/>
     * By default this is <tt>false</tt>. If configured as <tt>true</tt> then this listener will process the
     * {@link org.apache.camel.Exchange} asynchronous.
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    public synchronized RabbitTemplate getTemplate() {
        if (template == null) {
            template = endpoint.createInOnlyTemplate();
        }
        return template;
    }

    public void setTemplate(RabbitTemplate template) {
        this.template = template;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        LOG.trace("onMessage START");

        LOG.debug("{} consumer received RabbitMQ message: {}", endpoint, message);
        RuntimeCamelException rce;
        try {
            final Address replyDestination
                    = message.getMessageProperties() != null ? message.getMessageProperties().getReplyToAddress() : null;
            final boolean sendReply = !isDisableReplyTo() && replyDestination != null;
            final Exchange exchange = createExchange(message, channel, replyDestination);

            // process the exchange either asynchronously or synchronous
            LOG.trace("onMessage.process START");
            AsyncCallback callback
                    = new EndpointMessageListenerAsyncCallback(message, exchange, endpoint, sendReply, replyDestination);

            // async is by default false, which mean we by default will process the exchange synchronously
            // to keep backwards compatible, as well ensure this consumer will pickup messages in order
            // (eg to not consume the next message before the previous has been fully processed)
            // but if end user explicit configure consumerAsync=true, then we can process the message
            // asynchronously (unless endpoint has been configured synchronous, or we use transaction)
            boolean forceSync = endpoint.isSynchronous();
            if (forceSync || !isAsync()) {
                // must process synchronous if transacted or configured to do so
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchange {} synchronously", exchange.getExchangeId());
                }
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(true);
                }
            } else {
                // process asynchronous using the async routing engine
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchange {} asynchronously", exchange.getExchangeId());
                }
                boolean sync = processor.process(exchange, callback);
                if (!sync) {
                    // will be done async so return now
                    return;
                }
            }
            // if we failed processed the exchange from the async callback task, then grab the exception
            rce = exchange.getException(RuntimeCamelException.class);

            // release back when synchronous mode
            consumer.releaseExchange(exchange, false);
        } catch (Exception e) {
            rce = wrapRuntimeCamelException(e);
        }

        // an exception occurred so rethrow to trigger rollback on listener
        // the listener will use the error handler to handle the uncaught exception
        if (rce != null) {
            LOG.trace("onMessage END throwing exception: {}", rce.getMessage());
            // Spring message listener container will handle uncaught exceptions
            throw rce;
        }

        LOG.trace("onMessage END");
    }

    protected Exchange createExchange(Message message, Channel channel, Object replyDestination) {
        Exchange exchange = consumer.createExchange(false);
        exchange.setProperty(SpringRabbitMQConstants.CHANNEL, channel);

        Object body = endpoint.getMessageConverter().fromMessage(message);
        exchange.getMessage().setBody(body);

        Map<String, Object> headers
                = endpoint.getMessagePropertiesConverter().fromMessageProperties(message.getMessageProperties(), exchange);
        if (!headers.isEmpty()) {
            exchange.getMessage().setHeaders(headers);
        }

        // lets set to an InOut if we have some kind of reply-to destination
        if (replyDestination != null && !disableReplyTo) {
            // only change pattern if not already out capable
            if (!exchange.getPattern().isOutCapable()) {
                exchange.setPattern(ExchangePattern.InOut);
            }
        }
        return exchange;
    }

    /**
     * Callback task that is performed when the exchange has been processed
     */
    private final class EndpointMessageListenerAsyncCallback implements AsyncCallback {

        private final Message message;
        private final Exchange exchange;
        private final SpringRabbitMQEndpoint endpoint;
        private final boolean sendReply;
        private final Address replyDestination;

        private EndpointMessageListenerAsyncCallback(Message message, Exchange exchange, SpringRabbitMQEndpoint endpoint,
                                                     boolean sendReply, Address replyDestination) {
            this.message = message;
            this.exchange = exchange;
            this.endpoint = endpoint;
            this.sendReply = sendReply;
            this.replyDestination = replyDestination;
        }

        @Override
        public void done(boolean doneSync) {
            LOG.trace("onMessage.process END");

            // now we evaluate the processing of the exchange and determine if it was a success or failure
            // we also grab information from the exchange to be used for sending back a reply (if we are to do so)
            // so the following logic seems a bit complicated at first glance

            // if we send back a reply it can either be the message body or transferring a caused exception
            org.apache.camel.Message body = null;
            RuntimeCamelException rce = null;

            if (exchange.isFailed() || exchange.isRollbackOnly()) {
                if (exchange.isRollbackOnly()) {
                    // rollback only so wrap an exception so we can rethrow the exception to cause rollback
                    rce = wrapRuntimeCamelException(new RollbackExchangeException(exchange));
                } else if (exchange.getException() != null) {
                    // only throw exception if endpoint is not configured to transfer exceptions back to caller
                    // do not send a reply but wrap and rethrow the exception
                    rce = wrapRuntimeCamelException(exchange.getException());
                }
            } else {
                // process OK so get the reply body if we are InOut and has a body
                // If the ppl don't want to send the message back, he should use the InOnly
                if (sendReply && exchange.getPattern().isOutCapable()) {
                    body = exchange.getMessage();
                }
            }

            // send back reply if there was no error and we are supposed to send back a reply
            if (rce == null && sendReply && body != null) {
                LOG.trace("onMessage.sendReply START");
                try {
                    sendReply(replyDestination, message, exchange, body);
                } catch (Exception e) {
                    rce = new RuntimeCamelException(e);
                }
                LOG.trace("onMessage.sendReply END");
            }

            // if an exception occurred
            if (rce != null) {
                if (doneSync) {
                    // we were done sync, so put exception on exchange, so we can grab it in the onMessage
                    // method and rethrow it
                    exchange.setException(rce);
                } else {
                    // we were done async, so use the endpoint error handler
                    if (endpoint.getExceptionHandler() != null) {
                        endpoint.getExceptionHandler().handleException(rce);
                    }
                }
            }

            if (!doneSync) {
                // release back when in asynchronous mode
                consumer.releaseExchange(exchange, false);
            }
        }

        private void sendReply(Address replyDestination, Message message, Exchange exchange, org.apache.camel.Message out) {
            if (replyDestination == null) {
                LOG.debug("Cannot send reply message as there is no reply-to for: {}", out);
                return;
            }

            String cid = message.getMessageProperties().getCorrelationId();
            Object body = out.getBody();
            Message msg;
            if (body instanceof Message) {
                msg = (Message) body;
            } else {
                MessageProperties mp = endpoint.getMessagePropertiesConverter().toMessageProperties(exchange);
                mp.setCorrelationId(cid);
                msg = endpoint.getMessageConverter().toMessage(body, mp);
            }

            // send reply back
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} sending reply message [correlationId:{}]: {}", endpoint, cid, msg);
            }
            getTemplate().send(replyDestination.getExchangeName(), replyDestination.getRoutingKey(), msg);
        }
    }

}
