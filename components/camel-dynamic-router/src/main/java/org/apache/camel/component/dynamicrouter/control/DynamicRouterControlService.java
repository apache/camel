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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.Predicate;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilterStatistics;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_INVALID_PREDICATE_EXPRESSION;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_NO_PREDICATE_BEAN_FOUND;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_PREDICATE_CLASS;

/**
 * A service for managing dynamic routing subscriptions.
 */
@Converter(generateBulkLoader = true)
@ManagedResource(description = "Dynamic Router control operations service")
public class DynamicRouterControlService extends ServiceSupport {

    private final CamelContext camelContext;

    /**
     * The {@link DynamicRouterFilterService}.
     */
    private final DynamicRouterFilterService filterService;

    /**
     * Creates a new {@link DynamicRouterControlService}.
     *
     * @param camelContext  the camel context
     * @param filterService the filter service
     */
    public DynamicRouterControlService(CamelContext camelContext,
                                       DynamicRouterFilterService filterService) {
        this.camelContext = camelContext;
        this.filterService = filterService;
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
            final CamelContext camelContext,
            final String predExpression,
            final String expressionLanguage) {
        try {
            return camelContext.resolveLanguage(expressionLanguage).createPredicate(predExpression);
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
    static Predicate obtainPredicateFromBeanName(
            final String predicateBeanName,
            final CamelContext camelContext) {
        return Optional.ofNullable(CamelContextHelper.lookup(camelContext, predicateBeanName, Predicate.class))
                .orElseThrow(() -> new IllegalStateException(ERROR_NO_PREDICATE_BEAN_FOUND));
    }

    /**
     * Tries to obtain the predicate from the instance.
     *
     * @param  instance the message body
     * @return          the predicate
     */
    static Predicate obtainPredicateFromInstance(final Object instance) {
        if (Predicate.class.isAssignableFrom(instance.getClass())) {
            return (Predicate) instance;
        } else {
            throw new IllegalArgumentException(ERROR_PREDICATE_CLASS);
        }
    }

    /**
     * Subscribes for dynamic routing with a predicate expression.
     *
     * @param  subscribeChannel   the subscribe channel
     * @param  subscriptionId     the subscription ID
     * @param  destinationUri     the destination URI
     * @param  priority           the priority
     * @param  predicate          the predicate
     * @param  expressionLanguage the expression language
     * @param  update             whether to update the subscription if it already exists, or add a new one
     * @return                    the subscription ID for the added/updated subscription
     */
    @ManagedOperation(description = "Subscribe for dynamic routing with a predicate expression")
    public String subscribeWithPredicateExpression(
            String subscribeChannel,
            String subscriptionId,
            String destinationUri,
            int priority,
            String predicate,
            String expressionLanguage,
            boolean update) {
        return filterService.addFilterForChannel(subscriptionId, priority,
                obtainPredicateFromExpression(camelContext, predicate, expressionLanguage),
                destinationUri, subscribeChannel, update);
    }

    /**
     * Subscribes for dynamic routing with the name of a predicate bean in the registry.
     *
     * @param  subscribeChannel the subscribe channel
     * @param  subscriptionId   the subscription ID
     * @param  destinationUri   the destination URI
     * @param  priority         the priority
     * @param  predicateBean    the predicate bean name
     * @param  update           whether to update the subscription if it already exists, or add a new one
     * @return                  the subscription ID for the added/updated subscription
     */
    @ManagedOperation(description = "Subscribe for dynamic routing with the name of a predicate bean in the registry")
    public String subscribeWithPredicateBean(
            String subscribeChannel,
            String subscriptionId,
            String destinationUri,
            int priority,
            String predicateBean,
            boolean update) {
        return filterService.addFilterForChannel(subscriptionId, priority,
                obtainPredicateFromBeanName(predicateBean, camelContext),
                destinationUri, subscribeChannel, update);
    }

    /**
     * Subscribes for dynamic routing with a predicate instance.
     *
     * @param  subscribeChannel the subscribe channel
     * @param  subscriptionId   the subscription ID
     * @param  destinationUri   the destination URI
     * @param  priority         the priority
     * @param  predicate        the predicate instance
     * @param  update           whether to update the subscription if it already exists, or add a new one
     * @return                  the subscription ID for the added/updated subscription
     */
    @ManagedOperation(description = "Subscribe for dynamic routing with a predicate instance")
    public String subscribeWithPredicateInstance(
            String subscribeChannel,
            String subscriptionId,
            String destinationUri,
            int priority,
            Object predicate,
            boolean update) {
        return filterService.addFilterForChannel(subscriptionId, priority, obtainPredicateFromInstance(predicate),
                destinationUri, subscribeChannel, update);
    }

    /**
     * Subscribes for dynamic routing with a predicate expression.
     *
     * @param  subscribeChannel the subscribe channel
     * @param  subscriptionId   the subscription ID
     * @return                  true if the subscription was removed, false otherwise
     */
    @ManagedOperation(description = "Unsubscribe for dynamic routing on a channel by subscription ID")
    public boolean removeSubscription(
            String subscribeChannel,
            String subscriptionId) {
        return filterService.removeFilterById(subscriptionId, subscribeChannel);
    }

    /**
     * Retrieves a copy of the filter map.
     *
     * @return a copy of the filter map
     */
    @ManagedAttribute(description = "Get the map of filters for all dynamic router channels")
    public Map<String, ConcurrentSkipListSet<PrioritizedFilter>> getSubscriptionsMap() {
        return Map.copyOf(filterService.getFilterMap());
    }

    /**
     * Retrieves a copy of the filter statistics map.
     *
     * @return a copy of the filter statistics map
     */
    @ManagedAttribute(description = "Get the map of statistics for all dynamic router channels")
    public Map<String, List<PrioritizedFilterStatistics>> getSubscriptionsStatisticsMap() {
        return Map.copyOf(filterService.getFilterStatisticsMap());
    }

    /**
     * Retrieves a copy of the filter map for the provided channel.
     *
     * @param  subscribeChannel the subscribe channel
     * @return                  a copy of the filter map for the provided channel
     */
    public String getSubscriptionsForChannel(String subscribeChannel) {
        return filterService.getFiltersForChannel(subscribeChannel).stream()
                .map(PrioritizedFilter::toString)
                .collect(Collectors.joining(",\n", "[", "]"));
    }

    /**
     * Retrieves a copy of the filter statistics map for the provided channel.
     *
     * @param  subscribeChannel the subscribe channel
     * @return                  a copy of the filter statistics map for the provided channel
     */
    public String getStatisticsForChannel(String subscribeChannel) {
        return filterService.getFiltersForChannel(subscribeChannel).stream()
                .map(PrioritizedFilter::statistics)
                .map(PrioritizedFilterStatistics::toString)
                .collect(Collectors.joining(",\n", "[", "]"));
    }

    /**
     * Factory class for creating instances of {@link DynamicRouterControlService}.
     */
    public static class DynamicRouterControlServiceFactory {

        /**
         * Creates a new {@link DynamicRouterControlService}.
         *
         * @param  camelContext               the camel context
         * @param  dynamicRouterFilterService the filter service
         * @return                            a new {@link DynamicRouterControlService}
         */
        public DynamicRouterControlService getInstance(
                final CamelContext camelContext,
                final DynamicRouterFilterService dynamicRouterFilterService) {
            return new DynamicRouterControlService(camelContext, dynamicRouterFilterService);
        }
    }
}
