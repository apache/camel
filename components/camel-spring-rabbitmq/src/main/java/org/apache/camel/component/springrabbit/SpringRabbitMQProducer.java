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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.RabbitMessageFuture;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class SpringRabbitMQProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQProducer.class);

    private RabbitTemplate inOnlyTemplate;
    private AsyncRabbitTemplate inOutTemplate;

    public SpringRabbitMQProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringRabbitMQEndpoint getEndpoint() {
        return (SpringRabbitMQEndpoint) super.getEndpoint();
    }

    public RabbitTemplate getInOnlyTemplate() {
        if (inOutTemplate == null) {
            inOnlyTemplate = getEndpoint().createInOnlyTemplate();
        }
        return inOnlyTemplate;
    }

    public void setInOnlyTemplate(RabbitTemplate inOnlyTemplate) {
        this.inOnlyTemplate = inOnlyTemplate;
    }

    public AsyncRabbitTemplate getInOutTemplate() {
        if (inOutTemplate == null) {
            inOutTemplate = getEndpoint().createInOutTemplate();
        }
        inOutTemplate.start();
        return inOutTemplate;
    }

    public void setInOutTemplate(AsyncRabbitTemplate inOutTemplate) {
        this.inOutTemplate = inOutTemplate;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getEndpoint().isTestConnectionOnStartup()) {
            testConnectionOnStartup();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (inOnlyTemplate != null) {
            inOnlyTemplate.stop();
            inOnlyTemplate = null;
        }
        if (inOutTemplate != null) {
            inOutTemplate.stop();
            inOutTemplate = null;
        }
        super.doStop();
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
            if (!getEndpoint().isDisableReplyTo() && exchange.getPattern().isOutCapable()) {
                // in out requires a bit more work than in only
                return processInOut(exchange, callback);
            } else {
                // in only
                return processInOnly(exchange, callback);
            }
        } catch (Exception e) {
            // must catch exception to ensure callback is invoked as expected
            // to let Camel error handling deal with this
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean processInOut(Exchange exchange, AsyncCallback callback) {
        // header take precedence over endpoint
        final String exchangeName = getExchangeName(exchange);

        final String routingKey
                = getValue(exchange, SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY, getEndpoint().getRoutingKey());

        final Message msg = getMessage(exchange);

        try {
            // will use RabbitMQ direct reply-to
            RabbitMessageFuture future = getInOutTemplate().sendAndReceive(exchangeName, routingKey, msg);
            future.whenCompleteAsync((message, throwable) -> {
                try {
                    if (throwable != null) {
                        exchange.setException(throwable);
                    } else {
                        Object body1 = getEndpoint().getMessageConverter().fromMessage(message);
                        exchange.getMessage().setBody(body1);
                        Map<String, Object> headers
                                = getEndpoint().getMessagePropertiesConverter()
                                        .fromMessageProperties(message.getMessageProperties(), exchange);
                        if (!headers.isEmpty()) {
                            exchange.getMessage().getHeaders().putAll(headers);
                        }
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(false);
                }
            });

            return false;

        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    private Message getMessage(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        Message msg;
        if (body instanceof Message) {
            msg = (Message) body;
        } else {
            MessageProperties mp = getEndpoint().getMessagePropertiesConverter().toMessageProperties(exchange);
            msg = getEndpoint().getMessageConverter().toMessage(body, mp);
        }
        return msg;
    }

    protected boolean processInOnly(Exchange exchange, AsyncCallback callback) {
        // header take precedence over endpoint
        final String exchangeName = getExchangeName(exchange);

        final String routingKey
                = getValue(exchange, SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY, getEndpoint().getRoutingKey());

        final Message msg = getMessage(exchange);

        final String ex = exchangeName;
        final String rk = routingKey;
        boolean confirm;
        if ("auto".equalsIgnoreCase(getEndpoint().getConfirm())) {
            confirm = getEndpoint().getConnectionFactory().isPublisherConfirms();
        } else if ("enabled".equalsIgnoreCase(getEndpoint().getConfirm())) {
            confirm = true;
        } else {
            confirm = false;
        }
        final long timeout = getEndpoint().getConfirmTimeout() <= 0 ? Long.MAX_VALUE : getEndpoint().getConfirmTimeout();
        try {
            Boolean sent = getInOnlyTemplate().invoke(t -> {
                t.send(ex, rk, msg);
                if (confirm) {
                    return t.waitForConfirms(timeout);
                } else {
                    return true;
                }
            });
            if (Boolean.FALSE == sent) {
                exchange.setException(new TimeoutException("Message not sent within " + timeout + " millis"));
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    private String getValue(Exchange exchange, String routingOverrideKey, String defaultValue) {
        String routingKey = (String) exchange.getMessage().removeHeader(routingOverrideKey);
        if (routingKey == null) {
            return defaultValue;
        }
        return routingKey;
    }

    private String getExchangeName(Exchange exchange) {
        String exchangeName
                = getValue(exchange, SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME, getEndpoint().getExchangeName());
        return SpringRabbitMQHelper.isDefaultExchange(exchangeName) ? "" : exchangeName;
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateProducerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateProducerException {
        Connection conn = null;
        try {
            RabbitTemplate template = getInOnlyTemplate();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Testing RabbitMQ Connection on startup for: {}", getEndpoint().getConnectionFactory().getHost());
            }
            conn = template.getConnectionFactory().createConnection();

            LOG.debug("Successfully tested RabbitMQ Connection on startup for: {}",
                    getEndpoint().getConnectionFactory().getHost());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(getEndpoint(), e);
        } finally {
            RabbitUtils.closeConnection(conn);
        }
    }

}
