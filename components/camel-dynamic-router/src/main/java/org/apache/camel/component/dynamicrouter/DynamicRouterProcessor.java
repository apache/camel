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
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Traceable;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.dynamicrouter.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.builder.PredicateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_ALL_MATCH;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_FIRST_MATCH;

/**
 * Implements a <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router</a> pattern where the destinations
 * are computed at runtime. Recipients register rules and their endpoint with the dynamic router. For each message, each
 * registered recipient's rules are evaluated, and the message is routed to the first recipient that matches.
 */
@ManagedResource(description = "Managed Dynamic Router Processor")
public class DynamicRouterProcessor extends AsyncProcessorSupport implements Traceable, IdAware {

    /**
     * The logger for instances to log messages.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterProcessor.class);

    /**
     * Template for a logging endpoint, showing all, and multiline.
     */
    private static final String LOG_ENDPOINT = "log:%s.%s?level=%s&showAll=true&multiline=true";

    /**
     * {@link FilterProcessor}s, mapped by subscription ID, to determine if the incoming exchange should be routed based
     * on the content.
     */
    private final TreeMap<String, PrioritizedFilterProcessor> filterMap;

    /**
     * The camel context.
     */
    private final CamelContext camelContext;

    /**
     * Indicates the behavior of the Dynamic Router when routing participants are selected to receive an incoming
     * exchange. If the mode is "firstMatch", then the exchange is routed only to the first participant that has a
     * matching predicate. If the mode is "allMatch", then the exchange is routed to all participants that have a
     * matching predicate.
     */
    private final String recipientMode;

    /**
     * The tempate for sending messages.
     */
    private final ProducerTemplate producerTemplate;

    /**
     * The default processor to use if there are no matching processors to process the exchange.
     */
    private PrioritizedFilterProcessor defaultProcessor;

    /**
     * The {@link ExecutorService} for multicasting messages.
     */
    private ExecutorService executorService;

    /**
     * The {@link ReactiveExecutor} for scheduling message sending.
     */
    private ReactiveExecutor reactiveExecutor;

    /**
     * The {@link FilterProcessor} factory.
     */
    private final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier;

    /**
     * Flag to log a warning if a message is dropped due to no matching filters.
     */
    private final boolean warnDroppedMessage;

    /**
     * The id of this dynamic router processor.
     */
    private String id;

    /**
     * Create the processor instance with all properties.
     *
     * @param id                             the id of the processor
     * @param camelContext                   the camel context
     * @param recipientMode                  the recipient mode
     * @param warnDroppedMessage             flag to warn if messages are dropped
     * @param filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
     */
    public DynamicRouterProcessor(final String id, final CamelContext camelContext, final String recipientMode,
                                  final boolean warnDroppedMessage,
                                  final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
        this.id = id;
        this.filterMap = new TreeMap<>();
        this.camelContext = camelContext;
        this.recipientMode = recipientMode;
        this.producerTemplate = camelContext.createProducerTemplate();
        this.filterProcessorFactorySupplier = filterProcessorFactorySupplier;
        this.warnDroppedMessage = warnDroppedMessage;
        LOG.debug("Created Dynamic Router Processor");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        final ExtendedCamelContext extendedCamelContext = camelContext.getCamelContextExtension();
        this.reactiveExecutor = extendedCamelContext.getReactiveExecutor();
        this.executorService = camelContext.getExecutorServiceManager()
                .newDefaultThreadPool(this, "dynamicRouterMulticastPool");
        final String message = String.format(LOG_ENDPOINT, this.getClass().getCanonicalName(), getId(),
                warnDroppedMessage ? "WARN" : "DEBUG");
        this.defaultProcessor = filterProcessorFactorySupplier.get().getInstance(
                "defaultProcessor",
                Integer.MAX_VALUE, camelContext, PredicateBuilder.constant(true),
                exchange -> {
                    String error = String.format(
                            "DynamicRouter '%s': no filters matched for an exchange with id: '%s', from route: '%s'",
                            getId(), exchange.getExchangeId(), exchange.getFromEndpoint());
                    if (warnDroppedMessage) {
                        LOG.warn(error);
                    } else {
                        LOG.debug(error);
                    }
                    producerTemplate.send(message, exchange);
                });
    }

    /**
     * Convenience method to create a {@link PrioritizedFilterProcessor} from the details of the incoming
     * {@link DynamicRouterControlMessage} properties.
     *
     * @param  controlMessage the incoming control message
     * @return                a {@link PrioritizedFilterProcessor} built from the properties of the incoming control
     *                        message
     */
    PrioritizedFilterProcessor createFilter(final DynamicRouterControlMessage controlMessage) {
        final String id = controlMessage.getId();
        final int priority = controlMessage.getPriority();
        final String endpoint = controlMessage.getEndpoint();
        final Predicate predicate = controlMessage.getPredicate();
        final Processor processor = exchange -> producerTemplate.send(endpoint, exchange);
        return filterProcessorFactorySupplier.get().getInstance(id, priority, camelContext, predicate, processor);
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
    public void addFilter(final PrioritizedFilterProcessor filter) {
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
    public PrioritizedFilterProcessor getFilter(final String filterId) {
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
    List<PrioritizedFilterProcessor> matchFilters(final Exchange exchange) {
        return Optional.of(
                filterMap.values().stream()
                        .sorted()
                        .filter(f -> f.matches(exchange))
                        .limit(MODE_FIRST_MATCH.equals(recipientMode) ? 1 : Integer.MAX_VALUE)
                        .collect(Collectors.toList()))
                .filter(list -> !list.isEmpty())
                .orElse(Collections.singletonList(defaultProcessor));
    }

    /**
     * Processes the message exchange, where the caller supports having the exchange asynchronously processed. The
     * exchange is matched against all {@link #filterMap} to determine if any of them are suitable to handle the
     * exchange. When the first suitable filter is found, it processes the exchange.
     * <p/>
     * If there was any failure in processing, then the caused {@link Exception} would be set on the {@link Exchange}.
     *
     * @param  exchange the message exchange
     * @param  callback the {@link AsyncCallback} will be invoked when the processing of the exchange is completed. If
     *                  the exchange is completed synchronously, then the callback is also invoked synchronously. The
     *                  callback should therefore be careful of starting recursive loop.
     * @return          (doneSync) <tt>true</tt> to continue to execute synchronously, <tt>false</tt> to continue
     *                  execution asynchronously
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final List<PrioritizedFilterProcessor> matchingFilters = matchFilters(exchange);
        try {
            if (MODE_ALL_MATCH.equals(recipientMode)) {
                for (PrioritizedFilterProcessor processor : matchingFilters) {
                    Exchange copy = ExchangeHelper.createCopy(exchange, true);
                    executorService
                            .submit(() -> reactiveExecutor.schedule(() -> processor.process(copy, callback)));
                }
            } else {
                matchingFilters.stream()
                        .findFirst()
                        .ifPresent(p -> p.process(exchange, callback));
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        return false;
    }

    /**
     * The string representation of this dynamic router is its id.
     *
     * @return the id
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * For tracing.
     *
     * @return the label for tracing.
     */
    @Override
    public String getTraceLabel() {
        return getId();
    }

    /**
     * Returns the id of the instance of this processor.
     *
     * @return the id
     */
    @Override
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id of the instance of this processor.
     *
     * @param id the id
     */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Create a {@link DynamicRouterProcessor} instance.
     */
    public static class DynamicRouterProcessorFactory {

        /**
         * Create the processor instance with all properties.
         *
         * @param id                             the id of the processor
         * @param camelContext                   the camel context
         * @param recipientMode                  the mode for sending exchanges to matching participants
         * @param warnDroppedMessage             warn if no filters match an exchange
         * @param filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
         */
        public DynamicRouterProcessor getInstance(
                final String id,
                final CamelContext camelContext,
                final String recipientMode,
                final boolean warnDroppedMessage,
                final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
            return new DynamicRouterProcessor(
                    id, camelContext, recipientMode, warnDroppedMessage,
                    filterProcessorFactorySupplier);
        }
    }
}
