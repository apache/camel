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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.Channel;

public class RabbitMQDeclareSupport {

    private final RabbitMQEndpoint endpoint;

    RabbitMQDeclareSupport(final RabbitMQEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void declareAndBindExchangesAndQueuesUsing(final Channel channel) throws IOException {
        declareAndBindDeadLetterExchangeWithQueue(channel);
        declareAndBindExchangeWithQueue(channel);
    }

    private void declareAndBindDeadLetterExchangeWithQueue(final Channel channel) throws IOException {
        if (endpoint.getDeadLetterExchange() != null) {
            // TODO Do we need to setup the args for the DeadLetter?
            declareExchange(channel, endpoint.getDeadLetterExchange(), endpoint.getDeadLetterExchangeType(), Collections.<String, Object> emptyMap());
            declareAndBindQueue(channel, endpoint.getDeadLetterQueue(), endpoint.getDeadLetterExchange(), endpoint.getDeadLetterRoutingKey(), null, null);
        }
    }

    private void declareAndBindExchangeWithQueue(final Channel channel) throws IOException {
        if (shouldDeclareExchange()) {
            declareExchange(channel, endpoint.getExchangeName(), endpoint.getExchangeType(), resolvedExchangeArguments());
        }

        if (shouldDeclareQueue()) {
            // need to make sure the queueDeclare is same with the exchange
            // declare
            declareAndBindQueue(channel, endpoint.getQueue(), endpoint.getExchangeName(), endpoint.getRoutingKey(), resolvedQueueArguments(), endpoint.getBindingArgs());
        }
    }

    private Map<String, Object> resolvedQueueArguments() {
        Map<String, Object> queueArgs = new HashMap<>();
        populateQueueArgumentsFromDeadLetterExchange(queueArgs);
        populateQueueArgumentsFromConfigurer(queueArgs);
        queueArgs.putAll(endpoint.getQueueArgs());
        formatSpecialQueueArguments(queueArgs);
        return queueArgs;
    }

    private void formatSpecialQueueArguments(Map<String, Object> queueArgs) {
        // some arguments must be in numeric values so we need to fix this
        Object queueLengthLimit = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_LENGTH_LIMIT_KEY);
        if (queueLengthLimit instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_LENGTH_LIMIT_KEY, Long.parseLong((String) queueLengthLimit));
        }

        Object queueMessageTtl = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_MESSAGE_TTL_KEY);
        if (queueMessageTtl instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_MESSAGE_TTL_KEY, Long.parseLong((String) queueMessageTtl));
        }

        Object queueExpiration = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY);
        if (queueExpiration instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY, Long.parseLong((String) queueExpiration));
        }
    }

    private void populateQueueArgumentsFromDeadLetterExchange(final Map<String, Object> queueArgs) {
        if (endpoint.getDeadLetterExchange() != null) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_EXCHANGE, endpoint.getDeadLetterExchange());
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_ROUTING_KEY, endpoint.getDeadLetterRoutingKey());
        }
    }

    private Map<String, Object> resolvedExchangeArguments() {
        Map<String, Object> exchangeArgs = new HashMap<>();
        if (endpoint.getExchangeArgsConfigurer() != null) {
            endpoint.getExchangeArgsConfigurer().configurArgs(exchangeArgs);
        }
        exchangeArgs.putAll(endpoint.getExchangeArgs());
        return exchangeArgs;
    }

    private boolean shouldDeclareQueue() {
        return !endpoint.isSkipQueueDeclare() && endpoint.getQueue() != null;
    }

    private boolean shouldDeclareExchange() {
        return !endpoint.isSkipExchangeDeclare();
    }

    private boolean shouldBindQueue() {
        return !endpoint.isSkipQueueBind();
    }

    private void populateQueueArgumentsFromConfigurer(final Map<String, Object> queueArgs) {
        if (endpoint.getQueueArgsConfigurer() != null) {
            endpoint.getQueueArgsConfigurer().configurArgs(queueArgs);
        }
    }

    private void declareExchange(final Channel channel, final String exchange, final String exchangeType, final Map<String, Object> exchangeArgs) throws IOException {
        if (endpoint.isPassive()) {
            channel.exchangeDeclarePassive(exchange);
        } else {
            channel.exchangeDeclare(exchange, exchangeType, endpoint.isDurable(), endpoint.isAutoDelete(), exchangeArgs);
        }
    }

    private void declareAndBindQueue(final Channel channel, final String queue, final String exchange, final String routingKey, final Map<String, Object> queueArgs,
                                     final Map<String, Object> bindingArgs)

        throws IOException {

        if (endpoint.isPassive()) {
            channel.queueDeclarePassive(queue);
        } else {
            channel.queueDeclare(queue, endpoint.isDurable(), endpoint.isExclusive(), endpoint.isAutoDelete(), queueArgs);
        }
        if (shouldBindQueue()) {
            channel.queueBind(queue, exchange, emptyIfNull(routingKey), bindingArgs);
        }
    }

    private String emptyIfNull(final String routingKey) {
        return routingKey == null ? "" : routingKey;
    }
}
