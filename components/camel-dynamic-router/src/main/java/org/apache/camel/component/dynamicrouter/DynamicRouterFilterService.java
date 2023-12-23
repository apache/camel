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
package org.apache.camel.component.dynamicrouter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterComponent;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterConfiguration;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.PrioritizedFilter.*;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.FILTER_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.ORIGINAL_BODY_HEADER;

public class DynamicRouterFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterComponent.class);

    /**
     * Lists of {@link PrioritizedFilter}s, mapped by their channel.
     * <p>
     * Each list holds the filters for that routing channel.
     */
    private final Map<String, ConcurrentSkipListSet<PrioritizedFilter>> filterMap = new ConcurrentHashMap<>();

    /**
     * Creates a {@link PrioritizedFilter} instance.
     */
    private final Supplier<PrioritizedFilterFactory> filterFactorySupplier;

    public DynamicRouterFilterService() {
        this.filterFactorySupplier = FILTER_FACTORY_SUPPLIER;
        LOG.debug("Created Dynamic Router component");
    }

    public DynamicRouterFilterService(final Supplier<PrioritizedFilterFactory> filterFactorySupplier) {
        this.filterFactorySupplier = filterFactorySupplier;
        LOG.debug("Created Dynamic Router component");
    }

    public void initializeChannelFilters(final String channel) {
        filterMap.computeIfAbsent(channel, c -> new ConcurrentSkipListSet<>(DynamicRouterConstants.FILTER_COMPARATOR));
    }

    /**
     * Get the {@link PrioritizedFilter}s for the specified channel.
     *
     * @param  channel channel to obtain {@link PrioritizedFilter}s for
     * @return         {@link PrioritizedFilter}s for the specified channel
     */
    public ConcurrentSkipListSet<PrioritizedFilter> getFiltersForChannel(final String channel) {
        return filterMap.get(channel);
    }

    /**
     * Convenience method to create a {@link PrioritizedFilter} from the supplied parameters.
     *
     * @param  id        the filter identifier
     * @param  priority  the filter priority
     * @param  predicate the filter predicate
     * @param  endpoint  the filter endpoint
     * @return           a {@link PrioritizedFilter} built from the supplied parameters
     */
    public PrioritizedFilter createFilter(
            final String id, final int priority, final Predicate predicate, final String endpoint) {
        return filterFactorySupplier.get().getInstance(id, priority, predicate, endpoint);
    }

    /**
     * Creates a {@link PrioritizedFilter} from the supplied parameters, and adds it to the filters for the specified
     * channel.
     *
     * @param id        the filter identifier
     * @param priority  the filter priority
     * @param predicate the filter predicate
     * @param endpoint  the filter endpoint
     * @param channel   the channel that contains the filter
     */
    public void addFilterForChannel(
            final String id, final int priority, final Predicate predicate, final String endpoint,
            final String channel) {
        addFilterForChannel(createFilter(id, priority, predicate, endpoint), channel);
    }

    /**
     * Adds the filter to the list of filters, and ensure that the filters are sorted by priority after the insertion.
     *
     * @param filter the filter to add
     */
    public void addFilterForChannel(final PrioritizedFilter filter, final String channel) {
        if (filter != null) {
            Set<PrioritizedFilter> filters = filterMap.computeIfAbsent(channel,
                    c -> new ConcurrentSkipListSet<>(DynamicRouterConstants.FILTER_COMPARATOR));
            filters.add(filter);
            LOG.debug("Added subscription: {}", filter);
        }
    }

    /**
     * Return the filter with the supplied filter identifier. If there is no such filter, then return null.
     *
     * @param  filterId the filter identifier
     * @param  channel  the channel that contains the filter
     * @return          the filter with the supplied ID, or null
     */
    public PrioritizedFilter getFilterById(final String filterId, final String channel) {
        return (ObjectHelper.isEmpty(channel)
                ? filterMap.values().stream().flatMap(Collection::stream) : filterMap.get(channel).stream())
                .filter(f -> filterId.equals(f.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No filter exists with ID: " + filterId));
    }

    /**
     * Removes a filter with the ID from the control message.
     *
     * @param filterId the ID of the filter to remove
     */
    public void removeFilterById(final String filterId, final String channel) {
        String routerChannel = (ObjectHelper.isEmpty(channel))
                ? filterMap.keySet().stream()
                        .filter(ch -> filterMap.get(ch).stream().anyMatch(f -> filterId.equals(f.id())))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No filter exists with ID: " + filterId))
                : channel;
        if (filterMap.get(routerChannel).removeIf(f -> filterId.equals(f.id()))) {
            LOG.debug("Removed subscription: {}", filterId);
        } else {
            LOG.debug("No subscription exists with ID: {}", filterId);
        }
    }

    /**
     * Match the exchange against all {@link PrioritizedFilter}s for the specified channel to determine if any of them
     * are suitable to handle the exchange, then create a comma-delimited string of the filters' endpoints.
     * <p>
     * <strong>SIDE-EFFECT</strong>: If there are no matching filters, this method will modify the {@link Exchange}!
     * Without a matching filter, a message would otherwise be dropped without any notification, including log messages.
     * Instead, if no matching filters can be found, this method will store the original message body in a header named
     * by {@link DynamicRouterConstants#ORIGINAL_BODY_HEADER}. The message body will be changed to a string indicating
     * that "no filters matched" the exchange. If the {@link DynamicRouterConfiguration#isWarnDroppedMessage()} flag is
     * set to true, the message will be logged as a warning. Otherwise, it will be logged at the DEBUG level.
     *
     * @param  exchange           the message exchange
     * @param  channel            the dynamic router channel to get filters for
     * @param  firstMatchOnly     to only return the first match
     * @param  warnDroppedMessage if there are no matching filters found, this flag determines if the message will be
     *                            logged as a warning; otherwise, it will be logged at the DEBUG level
     * @return                    a comma-delimited string of endpoints from matching filters
     */
    public String getMatchingEndpointsForExchangeByChannel(
            final Exchange exchange,
            final String channel,
            final boolean firstMatchOnly,
            final boolean warnDroppedMessage) {
        List<String> matchingEndpoints = new ArrayList<>();
        for (PrioritizedFilter filter : filterMap.get(channel)) {
            if (filter.predicate().matches(exchange)) {
                matchingEndpoints.add(filter.endpoint());
                if (firstMatchOnly) {
                    break;
                }
            }
        }
        String recipients = String.join(",", matchingEndpoints);
        if (ObjectHelper.isEmpty(recipients)) {
            Message message = exchange.getMessage();
            message.setHeader(ORIGINAL_BODY_HEADER, message.getBody());
            recipients = String.format(DynamicRouterConstants.LOG_ENDPOINT, this.getClass().getCanonicalName(), channel,
                    warnDroppedMessage ? LoggingLevel.WARN : LoggingLevel.DEBUG);
            String error = String.format(
                    "DynamicRouter channel '%s': no filters matched for an exchange from route: '%s'.  " +
                                         "The 'originalBody' header contains the original message body.",
                    channel, exchange.getFromEndpoint());
            message.setBody(error, String.class);
        }
        return recipients;
    }

    public static class DynamicRouterFilterServiceFactory {

        public DynamicRouterFilterService getInstance() {
            return new DynamicRouterFilterService();
        }

        public DynamicRouterFilterService getInstance(final Supplier<PrioritizedFilterFactory> filterFactorySupplier) {
            return new DynamicRouterFilterService(filterFactorySupplier);
        }
    }
}
