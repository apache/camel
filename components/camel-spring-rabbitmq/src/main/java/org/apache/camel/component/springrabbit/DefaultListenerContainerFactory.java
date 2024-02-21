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

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.apache.camel.component.springrabbit.SpringRabbitMQConstants.*;

/**
 * Default {@link ListenerContainerFactory}.
 */
public class DefaultListenerContainerFactory implements ListenerContainerFactory {

    @Override
    public AbstractMessageListenerContainer createListenerContainer(SpringRabbitMQEndpoint endpoint) {
        AbstractMessageListenerContainer listener;
        if (endpoint.getMessageListenerContainerType().equalsIgnoreCase(SIMPLE_MESSAGE_LISTENER_CONTAINER)) {
            listener = new CamelSimpleMessageListenerContainer(endpoint);
        } else {
            listener = new CamelDirectMessageListenerContainer(endpoint);
        }

        if (endpoint.getQueues() != null) {
            listener.setQueueNames(endpoint.getQueues().split(","));
        }
        listener.setAcknowledgeMode(endpoint.getAcknowledgeMode());
        listener.setExclusive(endpoint.isExclusive());
        listener.setNoLocal(endpoint.isNoLocal());

        AmqpAdmin admin = endpoint.getComponent().getAmqpAdmin();
        if (endpoint.isAutoDeclare() && admin == null) {
            RabbitAdmin ra = new RabbitAdmin(endpoint.getConnectionFactory());
            ra.setIgnoreDeclarationExceptions(endpoint.getComponent().isIgnoreDeclarationExceptions());
            admin = ra;
        }

        listener.setAutoDeclare(endpoint.isAutoDeclare());
        listener.setAmqpAdmin(admin);
        if (endpoint.getComponent().getErrorHandler() != null) {
            listener.setErrorHandler(endpoint.getComponent().getErrorHandler());
        }

        listener.setPrefetchCount(endpoint.getPrefetchCount());
        listener.setShutdownTimeout(endpoint.getComponent().getShutdownTimeout());
        listener.setConsumerArguments(endpoint.getConsumerArgs());

        // consumer retry settings
        if (endpoint.getRetry() != null) {
            // use a custom
            listener.setAdviceChain(endpoint.getRetry());
        } else {
            RetryInterceptorBuilder<?, ?> builder = RetryInterceptorBuilder.stateless();
            if (endpoint.getMaximumRetryAttempts() > 0) {
                builder.retryPolicy(new SimpleRetryPolicy(endpoint.getMaximumRetryAttempts()));
            }
            if (endpoint.getRetryDelay() > 0) {
                FixedBackOffPolicy delay = new FixedBackOffPolicy();
                delay.setBackOffPeriod(endpoint.getRetryDelay());
                builder.backOffPolicy(delay);
            }
            if (endpoint.isRejectAndDontRequeue()) {
                builder.recoverer(new RejectAndDontRequeueRecoverer());
            }
            listener.setAdviceChain(builder.build());
        }

        return listener;
    }

}
