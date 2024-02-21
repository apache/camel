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
package org.apache.camel.component.dynamicrouter.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterConfiguration;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.FILTER_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.ORIGINAL_BODY_HEADER;

/**
 * A service that manages the {@link PrioritizedFilter}s for dynamic router subscriptions.
 */
public class DynamicRouterFilterService {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterFilterService.class);

    /**
     * Lists of {@link PrioritizedFilter}s, mapped by their channel.
     * <p>
     * Each list holds the filters for that routing channel.
     */
    private final Map<String, ConcurrentSkipListSet<PrioritizedFilter>> filterMap = new ConcurrentHashMap<>();

    /**
     * Lists of {@link PrioritizedFilterStatistics}, mapped by their channel.
     * <p>
     * Each list holds the routing statistics for filters for that routing channel.
     */
    private final Map<String, List<PrioritizedFilterStatistics>> filterStatisticsMap = new ConcurrentHashMap<>();

    /**
     * Supplier for the {@link PrioritizedFilterFactory} instance.
     */
    private final Supplier<PrioritizedFilterFactory> filterFactorySupplier;

    public DynamicRouterFilterService() {
        this.filterFactorySupplier = FILTER_FACTORY_SUPPLIER;
        LOG.debug("Created Dynamic Router component");
    }

    /**
     * Constructor that allows the {@link PrioritizedFilterFactory} supplier to be specified.
     *
     * @param filterFactorySupplier the {@link PrioritizedFilterFactory} supplier
     */
    public DynamicRouterFilterService(final Supplier<PrioritizedFilterFactory> filterFactorySupplier) {
        this.filterFactorySupplier = filterFactorySupplier;
        LOG.debug("Created Dynamic Router component");
    }

    /**
     * Initialize the filter list for the specified channel.
     *
     * @param channel channel to initialize filter list for
     */
    public void initializeChannelFilters(final String channel) {
        filterMap.computeIfAbsent(channel, c -> new ConcurrentSkipListSet<>(DynamicRouterConstants.FILTER_COMPARATOR));
        filterStatisticsMap.computeIfAbsent(channel, c -> Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Get a copy of the {@link PrioritizedFilter}s for the specified channel.
     *
     * @param  channel channel to obtain {@link PrioritizedFilter}s for
     * @return         {@link PrioritizedFilter}s for the specified channel
     */
    public Collection<PrioritizedFilter> getFiltersForChannel(final String channel) {
        return List.copyOf(filterMap.get(channel));
    }

    /**
     * Retrieves a copy of the filter map.
     *
     * @return a copy of the filter map
     */
    public Map<String, ConcurrentSkipListSet<PrioritizedFilter>> getFilterMap() {
        return Map.copyOf(filterMap);
    }

    /**
     * Get a copy of the {@link PrioritizedFilterStatistics} for the specified channel.
     *
     * @param  channel channel to obtain {@link PrioritizedFilterStatistics} for
     * @return         {@link PrioritizedFilterStatistics} for the specified channel
     */
    public List<PrioritizedFilterStatistics> getStatisticsForChannel(final String channel) {
        return List.copyOf(filterStatisticsMap.get(channel));
    }

    /**
     * Retrieves a copy of the filter statistics map.
     *
     * @return a copy of the filter statistics map
     */
    public Map<String, List<PrioritizedFilterStatistics>> getFilterStatisticsMap() {
        return Map.copyOf(filterStatisticsMap);
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
            final String id, final int priority, final Predicate predicate, final String endpoint,
            final PrioritizedFilterStatistics statistics) {
        return filterFactorySupplier.get().getInstance(id, priority, predicate, endpoint, statistics);
    }

    /**
     * Creates a {@link PrioritizedFilter} from the supplied parameters, and adds it to the filters for the specified
     * channel.
     *
     * @param  id        the filter identifier
     * @param  priority  the filter priority
     * @param  predicate the filter predicate
     * @param  endpoint  the filter endpoint
     * @param  channel   the channel that contains the filter
     * @param  update    flag if this is an update to the filter
     * @return           the ID of the added filter
     */
    public String addFilterForChannel(
            final String id, final int priority, final Predicate predicate, final String endpoint,
            final String channel, final boolean update) {
        return addFilterForChannel(createFilter(id, priority, predicate, endpoint, new PrioritizedFilterStatistics(id)),
                channel, update);
    }

    /**
     * Adds the filter to the list of filters, and ensure that the filters are sorted by priority after the insertion.
     *
     * @param  filter the filter to add
     * @return        the ID of the added filter
     */
    public String addFilterForChannel(final PrioritizedFilter filter, final String channel, final boolean update) {
        boolean filterExists = !filterMap.isEmpty() &&
                filterMap.get(channel).stream().anyMatch(f -> filter.id().equals(f.id()));
        boolean okToAdd = update == filterExists;
        if (okToAdd) {
            Set<PrioritizedFilter> filters = filterMap.computeIfAbsent(channel,
                    c -> new ConcurrentSkipListSet<>(DynamicRouterConstants.FILTER_COMPARATOR));
            filters.add(filter);
            List<PrioritizedFilterStatistics> filterStatistics = filterStatisticsMap.computeIfAbsent(channel,
                    c -> Collections.synchronizedList(new ArrayList<>()));
            filterStatistics.add(filter.statistics());
            LOG.debug("Added subscription: {}", filter);
            return filter.id();
        }
        return String.format("Error: Filter could not be %s -- existing filter found with matching ID: %b",
                update ? "updated" : "added", filterExists);
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
     * Removes a filter with the ID from the control message. This does not remove the
     * {@link PrioritizedFilterStatistics} instance, because the statistics still represent actions that happened, so
     * they should remain for statistics reporting.
     *
     * @param filterId the ID of the filter to remove
     */
    public boolean removeFilterById(final String filterId, final String channel) {
        String routerChannel = (ObjectHelper.isEmpty(channel))
                ? filterMap.keySet().stream()
                        .filter(ch -> filterMap.get(ch).stream().anyMatch(f -> filterId.equals(f.id())))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No filter exists with ID: " + filterId))
                : channel;
        if (filterMap.get(routerChannel).removeIf(f -> filterId.equals(f.id()))) {
            LOG.debug("Removed subscription: {}", filterId);
            return true;
        } else {
            LOG.debug("No subscription exists with ID: {}", filterId);
            return false;
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
                filter.statistics().incrementCount();
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

    /**
     * Factory to create a {@link DynamicRouterFilterService}.
     */
    public static class DynamicRouterFilterServiceFactory {

        /**
         * Creates a {@link DynamicRouterFilterService} instance.
         *
         * @param  filterFactorySupplier the {@link PrioritizedFilterFactory} supplier
         * @return                       a {@link DynamicRouterFilterService} instance
         */
        public DynamicRouterFilterService getInstance(final Supplier<PrioritizedFilterFactory> filterFactorySupplier) {
            return new DynamicRouterFilterService(filterFactorySupplier);
        }
    }
}
