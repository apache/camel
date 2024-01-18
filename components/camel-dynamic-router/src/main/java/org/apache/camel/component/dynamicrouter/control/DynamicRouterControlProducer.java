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
package org.apache.camel.component.dynamicrouter.control;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_HEADER;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_LIST;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_STATS;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_UNSUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_UPDATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_DESTINATION_URI;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_EXPRESSION_LANGUAGE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE_BEAN;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PRIORITY;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIBE_CHANNEL;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIPTION_ID;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_NO_PREDICATE_BEAN_FOUND;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.SIMPLE_LANGUAGE;

/**
 * A {@link org.apache.camel.Producer} implementation to process control channel messages for the Dynamic Router.
 */
public class DynamicRouterControlProducer extends HeaderSelectorProducer {

    /**
     * The {@link DynamicRouterControlService} for the Dynamic Router.
     */
    private final DynamicRouterControlService dynamicRouterControlService;

    /**
     * The configuration for the Dynamic Router.
     */
    private final DynamicRouterControlConfiguration configuration;

    /**
     * Create the {@link org.apache.camel.Producer} for the Dynamic Router with the supplied {@link Endpoint} URI.
     *
     * @param endpoint                    the {@link DynamicRouterEndpoint}
     * @param dynamicRouterControlService the {@link DynamicRouterControlService}
     * @param configuration               the configuration for the Dynamic Router
     */
    public DynamicRouterControlProducer(final DynamicRouterControlEndpoint endpoint,
                                        final DynamicRouterControlService dynamicRouterControlService,
                                        final DynamicRouterControlConfiguration configuration) {
        super(endpoint, CONTROL_ACTION_HEADER, configuration::getControlActionOrDefault);
        this.dynamicRouterControlService = dynamicRouterControlService;
        this.configuration = configuration;
    }

    /**
     * Create a filter from parameters in the message body.
     *
     * @param  dynamicRouterControlService the {@link DynamicRouterControlService}
     * @param  message                     the message, where the body contains a control message
     * @param  update                      whether to update an existing filter (true) or add a new one (false)
     * @return                             the ID of the added filter
     */
    static String subscribeFromMessage(
            final DynamicRouterControlService dynamicRouterControlService,
            final Message message, final boolean update) {
        DynamicRouterControlMessage messageBody = message.getBody(DynamicRouterControlMessage.class);
        String subscriptionId = messageBody.getSubscriptionId();
        String subscribeChannel = messageBody.getSubscribeChannel();
        String destinationUri = messageBody.getDestinationUri();
        String priority = String.valueOf(messageBody.getPriority());
        String predicate = messageBody.getPredicate();
        String predicateBean = messageBody.getPredicateBean();
        String expressionLanguage = messageBody.getExpressionLanguage();
        if (ObjectHelper.isNotEmpty(predicateBean)) {
            return dynamicRouterControlService.subscribeWithPredicateBean(subscribeChannel, subscriptionId,
                    destinationUri, Integer.parseInt(priority), predicateBean, update);
        } else if (ObjectHelper.isNotEmpty(predicate) && ObjectHelper.isNotEmpty(expressionLanguage)) {
            return dynamicRouterControlService.subscribeWithPredicateExpression(subscribeChannel, subscriptionId,
                    destinationUri, Integer.parseInt(priority), predicate, expressionLanguage, update);
        } else {
            throw new IllegalStateException(ERROR_NO_PREDICATE_BEAN_FOUND);
        }
    }

    /**
     * Create a filter from parameters in message headers.
     *
     * @param  dynamicRouterControlService the {@link DynamicRouterControlService}
     * @param  message                     the message, where the headers contain subscription params
     * @param  update                      whether to update an existing filter (true) or add a new one (false)
     * @return                             the ID of the added filter
     */
    static String subscribeFromHeaders(
            final DynamicRouterControlService dynamicRouterControlService,
            final Message message, final boolean update) {
        Map<String, Object> headers = message.getHeaders();
        String subscriptionId = (String) headers.get(CONTROL_SUBSCRIPTION_ID);
        String subscribeChannel = (String) headers.get(CONTROL_SUBSCRIBE_CHANNEL);
        String destinationUri = (String) headers.get(CONTROL_DESTINATION_URI);
        String priority = String.valueOf(headers.get(CONTROL_PRIORITY));
        String predicate = (String) headers.get(CONTROL_PREDICATE);
        String predicateBean = (String) headers.get(CONTROL_PREDICATE_BEAN);
        String expressionLanguage = Optional.ofNullable((String) headers.get(CONTROL_EXPRESSION_LANGUAGE))
                .orElse(SIMPLE_LANGUAGE);
        if (ObjectHelper.isNotEmpty(predicateBean)) {
            return dynamicRouterControlService.subscribeWithPredicateBean(subscribeChannel, subscriptionId,
                    destinationUri, Integer.parseInt(priority), predicateBean, update);
        } else if (ObjectHelper.isNotEmpty(predicate) && ObjectHelper.isNotEmpty(expressionLanguage)) {
            return dynamicRouterControlService.subscribeWithPredicateExpression(subscribeChannel, subscriptionId,
                    destinationUri, Integer.parseInt(priority), predicate, expressionLanguage, update);
        } else {
            return dynamicRouterControlService.subscribeWithPredicateInstance(subscribeChannel, subscriptionId,
                    destinationUri, Integer.parseInt(priority), message.getBody(), update);
        }
    }

    /**
     * Performs "subscribe" if the {@link DynamicRouterControlConstants#CONTROL_ACTION_HEADER} header has a value of
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_SUBSCRIBE}.
     *
     * @param message  the incoming message from the exchange
     * @param callback the async callback
     */
    @InvokeOnHeader(CONTROL_ACTION_SUBSCRIBE)
    public void performSubscribe(final Message message, AsyncCallback callback) {
        String filterId;
        if (message.getBody() instanceof DynamicRouterControlMessage) {
            filterId = subscribeFromMessage(dynamicRouterControlService, message, false);
        } else {
            filterId = subscribeFromHeaders(dynamicRouterControlService, message, false);
        }
        message.setBody(filterId);
        callback.done(false);
    }

    /**
     * Performs "unsubscribe" if the {@link DynamicRouterControlConstants#CONTROL_ACTION_HEADER} header has a value of
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_UNSUBSCRIBE}.
     *
     * @param message  the incoming message from the exchange
     * @param callback the async callback
     */
    @InvokeOnHeader(CONTROL_ACTION_UNSUBSCRIBE)
    public void performUnsubscribe(final Message message, AsyncCallback callback) {
        Map<String, Object> headers = message.getHeaders();
        String subscriptionId = (String) headers.getOrDefault(CONTROL_SUBSCRIPTION_ID, configuration.getSubscriptionId());
        String subscribeChannel = (String) headers.getOrDefault(CONTROL_SUBSCRIBE_CHANNEL, configuration.getSubscribeChannel());
        boolean result = dynamicRouterControlService.removeSubscription(subscribeChannel, subscriptionId);
        message.setBody(result, boolean.class);
        callback.done(false);
    }

    /**
     * Performs "update" if the {@link DynamicRouterControlConstants#CONTROL_ACTION_HEADER} header has a value of
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_UPDATE}.
     *
     * @param message  the incoming message from the exchange
     * @param callback the async callback
     */
    @InvokeOnHeader(CONTROL_ACTION_UPDATE)
    public void performUpdate(final Message message, AsyncCallback callback) {
        String filterId;
        if (message.getBody() instanceof DynamicRouterControlMessage) {
            filterId = subscribeFromMessage(dynamicRouterControlService, message, true);
        } else {
            filterId = subscribeFromHeaders(dynamicRouterControlService, message, true);
        }
        message.setBody(filterId);
        callback.done(false);
    }

    /**
     * Performs a "list" of the subscriptions of the channel if the
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_HEADER} header has a value of
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_LIST}.
     *
     * @param exchange the incoming exchange
     * @param callback the async callback
     */
    @InvokeOnHeader(CONTROL_ACTION_LIST)
    public void performList(final Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();
        Map<String, Object> headers = message.getHeaders();
        String subscribeChannel = (String) headers.getOrDefault(CONTROL_SUBSCRIBE_CHANNEL, configuration.getSubscribeChannel());
        try {
            String filters = dynamicRouterControlService.getSubscriptionsForChannel(subscribeChannel);
            message.setBody(filters, String.class);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(false);
        }
    }

    /**
     * Performs the retrieval of routing "statistics" of the channel if the
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_HEADER} header has a value of
     * {@link DynamicRouterControlConstants#CONTROL_ACTION_STATS}.
     *
     * @param exchange the incoming exchange
     * @param callback the async callback
     */
    @InvokeOnHeader(CONTROL_ACTION_STATS)
    public void performStats(final Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();
        Map<String, Object> headers = message.getHeaders();
        String subscribeChannel = (String) headers.getOrDefault(CONTROL_SUBSCRIBE_CHANNEL, configuration.getSubscribeChannel());
        try {
            String stats = dynamicRouterControlService.getStatisticsForChannel(subscribeChannel);
            message.setBody(stats, String.class);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(false);
        }
    }

    /**
     * Create a {@link DynamicRouterControlProducer} instance.
     */
    public static class DynamicRouterControlProducerFactory {

        /**
         * Create the {@link org.apache.camel.Producer} for the Dynamic Router with the supplied {@link Endpoint} URI.
         *
         * @param endpoint                    the {@link DynamicRouterEndpoint}
         * @param dynamicRouterControlService the {@link DynamicRouterControlService}
         * @param configuration               the configuration for the Dynamic Router Control
         */
        public DynamicRouterControlProducer getInstance(
                final DynamicRouterControlEndpoint endpoint,
                final DynamicRouterControlService dynamicRouterControlService,
                final DynamicRouterControlConfiguration configuration) {
            return new DynamicRouterControlProducer(endpoint, dynamicRouterControlService, configuration);
        }
    }
}
