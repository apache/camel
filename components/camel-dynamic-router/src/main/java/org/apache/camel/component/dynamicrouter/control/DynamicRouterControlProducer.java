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
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.spi.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderSelectorProducer;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_HEADER;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_LIST;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_UNSUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_DESTINATION_URI;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_EXPRESSION_LANGUAGE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE_BEAN;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PRIORITY;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIBE_CHANNEL;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIPTION_ID;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_INVALID_PREDICATE_EXPRESSION;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_NO_PREDICATE_BEAN_FOUND;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_PREDICATE_CLASS;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.SIMPLE_LANGUAGE;

/**
 * A {@link org.apache.camel.Producer} implementation to process control channel messages for the Dynamic Router.
 */
@Converter(generateBulkLoader = true)
public class DynamicRouterControlProducer extends HeaderSelectorProducer {

    /**
     * The {@link DynamicRouterFilterService}.
     */
    private final DynamicRouterFilterService filterService;

    /**
     * The configuration for the Dynamic Router.
     */
    private final DynamicRouterControlConfiguration configuration;

    /**
     * Create the {@link org.apache.camel.Producer} for the Dynamic Router with the supplied {@link Endpoint} URI.
     *
     * @param endpoint      the {@link DynamicRouterEndpoint}
     * @param filterService the {@link DynamicRouterFilterService}
     * @param configuration the configuration for the Dynamic Router
     */
    public DynamicRouterControlProducer(final DynamicRouterControlEndpoint endpoint,
                                        final DynamicRouterFilterService filterService,
                                        final DynamicRouterControlConfiguration configuration) {
        super(endpoint, CONTROL_ACTION_HEADER, configuration::getControlActionOrDefault);
        this.filterService = filterService;
        this.configuration = configuration;
    }

    /**
     * Tries to obtain the predicate from the message body.
     *
     * @param  body the message body
     * @return      the predicate
     */
    static Predicate obtainPredicateFromMessageBody(final Object body) {
        if (Predicate.class.isAssignableFrom(body.getClass())) {
            return (Predicate) body;
        } else {
            throw new IllegalArgumentException(ERROR_PREDICATE_CLASS);
        }
    }

    /**
     * Tries to obtain the predicate from the expression of the provided language.
     *
     * @param  camelContext       the camel context
     * @param  predExpression     the predicate expression
     * @param  expressionLanguage the expression language
     * @return                    the predicate
     */
    static Predicate obtainPredicateFromExpression(
            final CamelContext camelContext, final String predExpression, final String expressionLanguage) {
        try {
            Language language = camelContext.resolveLanguage(expressionLanguage);
            return language.createPredicate(predExpression);
        } catch (Exception e) {
            String message = String.format(ERROR_INVALID_PREDICATE_EXPRESSION, expressionLanguage, predExpression);
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Tries to obtain the predicate of the provided bean name from the registry.
     *
     * @param  predicateBeanName the predicate bean name
     * @param  camelContext      the camel context
     * @return                   the predicate
     */
    @Converter
    static Predicate obtainPredicateFromBeanName(final String predicateBeanName, final CamelContext camelContext) {
        return Optional.ofNullable(CamelContextHelper.lookup(camelContext, predicateBeanName, Predicate.class))
                .orElseThrow(() -> new IllegalStateException(ERROR_NO_PREDICATE_BEAN_FOUND));
    }

    /**
     * Obtains the subscription {@link Predicate}. First, it looks for a bean in the registry. If that is not provided,
     * it looks at the predicate expression. If that is not provided, it looks at the message body.
     *
     * @param  body               body to inspect for a {@link Predicate} if the URI parameters does not have an
     *                            expression
     * @param  expressionLanguage the expression language
     * @param  predExpression     the predicate expression
     * @param  predicateBeanName  the predicate bean name
     * @return                    the {@link Predicate}
     */
    static Predicate obtainPredicate(
            final CamelContext camelContext,
            final Object body, final String expressionLanguage, final String predExpression, String predicateBeanName) {
        Predicate predicate;
        if (predicateBeanName != null && !predicateBeanName.isEmpty()) {
            predicate = obtainPredicateFromBeanName(predicateBeanName, camelContext);
        } else if (predExpression != null && !predExpression.isEmpty()
                && expressionLanguage != null && !expressionLanguage.isEmpty()) {
            predicate = obtainPredicateFromExpression(camelContext, predExpression, expressionLanguage);
        } else {
            predicate = obtainPredicateFromMessageBody(body);
        }
        return predicate;
    }

    /**
     * Create a filter from parameters in the message body.
     *
     * @param camelContext  the camel context
     * @param message       the message, where the body contains a control message
     * @param filterService the service for creating filters
     */
    static void subscribeFromMessage(
            final CamelContext camelContext, final Message message, final DynamicRouterFilterService filterService) {
        DynamicRouterControlMessage messageBody = message.getBody(DynamicRouterControlMessage.class);
        String subscriptionId = messageBody.getSubscriptionId();
        String subscribeChannel = messageBody.getSubscribeChannel();
        String destinationUri = messageBody.getDestinationUri();
        String priority = String.valueOf(messageBody.getPriority());
        String predicate = messageBody.getPredicate();
        String predicateBean = messageBody.getPredicateBean();
        String expressionLanguage = messageBody.getExpressionLanguage();
        filterService.addFilterForChannel(subscriptionId, Integer.parseInt(priority),
                obtainPredicate(camelContext, message.getBody(), expressionLanguage, predicate, predicateBean),
                destinationUri, subscribeChannel);
    }

    /**
     * Create a filter from parameters in message headers.
     *
     * @param camelContext  the camel context
     * @param message       the message, where the headers contain subscription params
     * @param filterService the service for creating filters
     */
    static void subscribeFromHeaders(
            final CamelContext camelContext, final Message message, final DynamicRouterFilterService filterService) {
        Map<String, Object> headers = message.getHeaders();
        String subscriptionId = (String) headers.get(CONTROL_SUBSCRIPTION_ID);
        String subscribeChannel = (String) headers.get(CONTROL_SUBSCRIBE_CHANNEL);
        String destinationUri = (String) headers.get(CONTROL_DESTINATION_URI);
        String priority = String.valueOf(headers.get(CONTROL_PRIORITY));
        String predicate = (String) headers.get(CONTROL_PREDICATE);
        String predicateBean = (String) headers.get(CONTROL_PREDICATE_BEAN);
        String expressionLanguage = Optional.ofNullable((String) headers.get(CONTROL_EXPRESSION_LANGUAGE))
                .orElse(SIMPLE_LANGUAGE);
        filterService.addFilterForChannel(subscriptionId, Integer.parseInt(priority),
                obtainPredicate(camelContext, message.getBody(), expressionLanguage, predicate, predicateBean),
                destinationUri, subscribeChannel);
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
        if (message.getBody() instanceof DynamicRouterControlMessage) {
            subscribeFromMessage(getCamelContext(), message, filterService);
        } else {
            subscribeFromHeaders(getCamelContext(), message, filterService);
        }
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
        filterService.removeFilterById(subscriptionId, subscribeChannel);
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
            String filtersJson = filterService.getFiltersForChannel(subscribeChannel).stream()
                    .map(PrioritizedFilter::toString)
                    .collect(Collectors.joining(",\n", "[", "]"));
            message.setBody(filtersJson, String.class);
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
         * @param endpoint the {@link DynamicRouterEndpoint}
         */
        public DynamicRouterControlProducer getInstance(
                final DynamicRouterControlEndpoint endpoint,
                final DynamicRouterFilterService filterService,
                final DynamicRouterControlConfiguration configuration) {
            return new DynamicRouterControlProducer(endpoint, filterService, configuration);
        }
    }
}
