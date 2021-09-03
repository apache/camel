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
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.concurrent.ListenableFutureCallback;

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
        String exchangeName = (String) exchange.getMessage().removeHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME);
        if (exchangeName == null) {
            exchangeName = getEndpoint().getExchangeName();
        }
        exchangeName = SpringRabbitMQHelper.isDefaultExchange(exchangeName) ? "" : exchangeName;

        String routingKey = (String) exchange.getMessage().removeHeader(SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY);
        if (routingKey == null) {
            routingKey = getEndpoint().getRoutingKey();
        }

        Object body = exchange.getMessage().getBody();
        Message msg;
        if (body instanceof Message) {
            msg = (Message) body;
        } else {
            MessageProperties mp = getEndpoint().getMessagePropertiesConverter().toMessageProperties(exchange);
            msg = getEndpoint().getMessageConverter().toMessage(body, mp);
        }

        try {
            // will use RabbitMQ direct reply-to
            AsyncRabbitTemplate.RabbitMessageFuture future = getInOutTemplate().sendAndReceive(exchangeName, routingKey, msg);
            future.addCallback(new ListenableFutureCallback<Message>() {
                @Override
                public void onFailure(Throwable throwable) {
                    exchange.setException(throwable);
                    callback.done(false);
                }

                @Override
                public void onSuccess(Message message) {
                    try {
                        Object body = getEndpoint().getMessageConverter().fromMessage(message);
                        exchange.getMessage().setBody(body);
                        Map<String, Object> headers
                                = getEndpoint().getMessagePropertiesConverter()
                                        .fromMessageProperties(message.getMessageProperties(), exchange);
                        if (!headers.isEmpty()) {
                            exchange.getMessage().getHeaders().putAll(headers);
                        }
                    } catch (Exception e) {
                        exchange.setException(e);
                    } finally {
                        callback.done(false);
                    }
                }
            });

            return false;

        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    protected boolean processInOnly(Exchange exchange, AsyncCallback callback) {
        // header take precedence over endpoint
        String exchangeName = (String) exchange.getMessage().removeHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME);
        if (exchangeName == null) {
            exchangeName = getEndpoint().getExchangeName();
        }
        exchangeName = SpringRabbitMQHelper.isDefaultExchange(exchangeName) ? "" : exchangeName;

        String routingKey = (String) exchange.getMessage().removeHeader(SpringRabbitMQConstants.ROUTING_OVERRIDE_KEY);
        if (routingKey == null) {
            routingKey = getEndpoint().getRoutingKey();
        }

        Object body = exchange.getMessage().getBody();
        Message msg;
        if (body instanceof Message) {
            msg = (Message) body;
        } else {
            MessageProperties mp = getEndpoint().getMessagePropertiesConverter().toMessageProperties(exchange);
            msg = getEndpoint().getMessageConverter().toMessage(body, mp);
        }

        try {
            getInOnlyTemplate().send(exchangeName, routingKey, msg);
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
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
