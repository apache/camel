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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.apache.camel.*;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.ProcessorExchangePair;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.support.*;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_FIRST_MATCH;

public class DynamicRouterMulticastProcessor extends MulticastProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterMulticastProcessor.class);

    /**
     * Template for a logging endpoint, showing all, and multiline.
     */
    private static final String LOG_ENDPOINT = "log:%s.%s?level=%s&showAll=true&multiline=true";

    private boolean ignoreInvalidEndpoints;

    private final ProducerCache producerCache;

    /**
     * {@link FilterProcessor}s, mapped by subscription ID, to determine if the incoming exchange should be routed based
     * on the content.
     */
    private final TreeMap<String, PrioritizedFilter> filterMap;

    /**
     * Indicates the behavior of the Dynamic Router when routing participants are selected to receive an incoming
     * exchange. If the mode is "firstMatch", then the exchange is routed only to the first participant that has a
     * matching predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a
     * matching predicate.
     */
    private final String recipientMode;

    /**
     * The {@link FilterProcessor} factory.
     */
    private final Supplier<PrioritizedFilter.PrioritizedFilterFactory> filterProcessorFactorySupplier;

    /**
     * Flag to log a warning if a message is dropped due to no matching filters.
     */
    private final boolean warnDroppedMessage;

    public DynamicRouterMulticastProcessor(String id, CamelContext camelContext, Route route, String recipientMode,
                                           final boolean warnDroppedMessage,
                                           final Supplier<PrioritizedFilter.PrioritizedFilterFactory> filterProcessorFactorySupplier,
                                           ProducerCache producerCache, AggregationStrategy aggregationStrategy,
                                           boolean parallelProcessing, ExecutorService executorService,
                                           boolean shutdownExecutorService,
                                           boolean streaming, boolean stopOnException,
                                           long timeout, Processor onPrepare, boolean shareUnitOfWork,
                                           boolean parallelAggregate) {
        super(camelContext, route, new ArrayList<>(), aggregationStrategy, parallelProcessing, executorService,
              shutdownExecutorService,
              streaming, stopOnException, timeout, onPrepare,
              shareUnitOfWork, parallelAggregate);
        setId(id);
        this.producerCache = producerCache;
        this.filterMap = new TreeMap<>();
        this.recipientMode = recipientMode;
        this.filterProcessorFactorySupplier = filterProcessorFactorySupplier;
        this.warnDroppedMessage = warnDroppedMessage;
    }

    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    private List<String> checkRecipients(Exchange exchange) {
        List<PrioritizedFilter> matchingFilters = matchFilters(exchange);
        Set<String> recipients = new HashSet<>();
        for (PrioritizedFilter filter : matchingFilters) {
            recipients.add(filter.getEndpoint().trim());
        }
        if (recipients.isEmpty()) {
            Message exchangeIn = exchange.getIn();
            Object originalBody = exchangeIn.getBody();
            exchangeIn.setHeader("originalBody", originalBody);
            String endpoint = String.format(LOG_ENDPOINT, this.getClass().getCanonicalName(), getId(),
                    warnDroppedMessage ? "WARN" : "DEBUG");
            recipients.add(endpoint);
            String error = String.format(
                    "DynamicRouter '%s': no filters matched for an exchange with id: '%s', from route: '%s'.  " +
                                         "The 'originalBody' header contains the original message body.",
                    getId(), exchange.getExchangeId(), exchange.getFromEndpoint());
            exchangeIn.setBody(error, String.class);
        }
        return new ArrayList<>(recipients);
    }

    protected List<Processor> createEndpointProcessors(Exchange exchange) {
        List<Processor> endpointProcessors = new ArrayList<>();
        List<String> recipientList = checkRecipients(exchange);
        for (String recipient : recipientList) {
            try {
                Endpoint ctxEndpoint = exchange.getContext().hasEndpoint(recipient);
                Endpoint endpoint = ctxEndpoint == null ? ExchangeHelper.resolveEndpoint(exchange, recipient) : ctxEndpoint;
                Producer producer = producerCache.acquireProducer(endpoint);
                Route route = ExchangeHelper.getRoute(exchange);
                endpointProcessors.add(wrapInErrorHandler(route, exchange, producer));
            } catch (Exception e) {
                if (isIgnoreInvalidEndpoints()) {
                    LOG.debug("Endpoint uri is invalid: {}. This exception will be ignored.", recipient, e);
                } else {
                    // failure so break out
                    throw e;
                }
            }
        }
        return endpointProcessors;
    }

    @Override
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) throws Exception {
        List<Processor> processors = createEndpointProcessors(exchange);
        this.getProcessors().clear();
        this.getProcessors().addAll(processors);
        return super.createProcessorExchangePairs(exchange);
    }

    /**
     * Convenience method to create a {@link PrioritizedFilter} from the details of the incoming
     * {@link DynamicRouterControlMessage} properties.
     *
     * @param  controlMessage the incoming control message
     * @return                a {@link PrioritizedFilter} built from the properties of the incoming control message
     */
    PrioritizedFilter createFilter(final DynamicRouterControlMessage controlMessage) {
        final String id = controlMessage.getId();
        final int priority = controlMessage.getPriority();
        final String endpoint = controlMessage.getEndpoint();
        final Predicate predicate = controlMessage.getPredicate();
        return filterProcessorFactorySupplier.get().getInstance(id, priority, predicate, endpoint);
    }

    /**
     * Add a filter based on the supplied control message properties for exchange routing evaluation.
     *
     * @param controlMessage the message for filter creation
     */
    public void addFilter(final DynamicRouterControlMessage controlMessage) {
        addFilter(createFilter(controlMessage));
    }

    /**
     * Adds the filter to the list of filters, and ensure that the filters are sorted by priority after the insertion.
     *
     * @param filter the filter to add
     */
    public void addFilter(final PrioritizedFilter filter) {
        synchronized (filterMap) {
            if (filter != null) {
                filterMap.put(filter.getId(), filter);
                LOG.debug("Added subscription: {}", filter);
            }
        }
    }

    /**
     * Return the filter with the supplied filter identifier. If there is no such filter, then return null.
     *
     * @param  filterId the filter identifier
     * @return          the filter with the supplied ID, or null
     */
    public PrioritizedFilter getFilter(final String filterId) {
        return filterMap.get(filterId);
    }

    /**
     * Removes a filter with the ID from the control message.
     *
     * @param filterId the ID of the filter to remove
     */
    public void removeFilter(final String filterId) {
        synchronized (filterMap) {
            Optional.ofNullable(filterMap.remove(filterId))
                    .ifPresentOrElse(
                            f -> LOG.debug("Removed subscription: {}", f),
                            () -> LOG.debug("No subscription exists with ID: {}", filterId));
        }
    }

    /**
     * Match the exchange against all {@link #filterMap} to determine if any of them are suitable to handle the
     * exchange.
     *
     * @param  exchange the message exchange
     * @return          list of filters that match for the exchange; if "firstMatch" mode, it is a singleton list of
     *                  that filter
     */
    protected List<PrioritizedFilter> matchFilters(final Exchange exchange) {
        return filterMap.values().stream()
                .sorted(PrioritizedFilter.COMPARATOR)
                .filter(f -> f.getPredicate().matches(exchange))
                .limit(MODE_FIRST_MATCH.equals(recipientMode) ? 1 : Integer.MAX_VALUE)
                .toList();
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        ServiceHelper.buildService(producerCache);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(producerCache);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() {
        // no-op because this processor has to keep running
        // in order to process while the containing route
        // and the component is active
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producerCache);
        super.doShutdown();
    }

    @Override
    public String getTraceLabel() {
        return getId();
    }

    public static class DynamicRouterRecipientListProcessorFactory {

        public DynamicRouterMulticastProcessor getInstance(
                String id,
                CamelContext camelContext, Route route, String recipientMode,
                final boolean warnDroppedMessage,
                final Supplier<PrioritizedFilter.PrioritizedFilterFactory> filterProcessorFactorySupplier,
                ProducerCache producerCache, AggregationStrategy aggregationStrategy,
                boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                boolean streaming, boolean stopOnException,
                long timeout, Processor onPrepare, boolean shareUnitOfWork, boolean parallelAggregate) {
            return new DynamicRouterMulticastProcessor(
                    id,
                    camelContext, route, recipientMode, warnDroppedMessage,
                    filterProcessorFactorySupplier, producerCache, aggregationStrategy, parallelProcessing,
                    executorService, shutdownExecutorService, streaming, stopOnException, timeout, onPrepare,
                    shareUnitOfWork, parallelAggregate);
        }
    }
}
