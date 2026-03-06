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
package org.apache.camel.impl.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryEntry;
import org.apache.camel.spi.ErrorRegistryView;
import org.apache.camel.support.EventNotifierSupport;

/**
 * Default {@link ErrorRegistry} implementation that listens to exchange failure events and captures error snapshots.
 */
public class DefaultErrorRegistry extends EventNotifierSupport implements ErrorRegistry, NonManagedService {

    private final ConcurrentLinkedDeque<ErrorRegistryEntry> entries = new ConcurrentLinkedDeque<>();
    private volatile boolean enabled;
    private volatile int maximumEntries = 100;
    private volatile Duration timeToLive = Duration.ofHours(1);
    private volatile boolean stackTraceEnabled;

    public DefaultErrorRegistry() {
        // only listen to exchange failure events
        setIgnoreCamelContextEvents(true);
        setIgnoreCamelContextInitEvents(true);
        setIgnoreRouteEvents(true);
        setIgnoreServiceEvents(true);
        setIgnoreExchangeCreatedEvent(true);
        setIgnoreExchangeCompletedEvent(true);
        setIgnoreExchangeRedeliveryEvents(true);
        setIgnoreExchangeSentEvents(true);
        setIgnoreExchangeSendingEvents(true);
        setIgnoreExchangeAsyncProcessingStartedEvents(true);
        setIgnoreStepEvents(true);
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (!enabled) {
            return;
        }
        if (event instanceof CamelEvent.ExchangeFailedEvent e) {
            capture(e.getExchange(), false);
        } else if (event instanceof CamelEvent.ExchangeFailureHandledEvent e) {
            capture(e.getExchange(), true);
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return enabled;
    }

    @Override
    public boolean isDisabled() {
        return !enabled;
    }

    private void capture(Exchange exchange, boolean handled) {
        Throwable exception;
        if (handled) {
            // when handled, the exception has been moved to EXCEPTION_CAUGHT property
            exception = exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Throwable.class);
        } else {
            exception = exchange.getException();
        }
        if (exception == null) {
            return;
        }

        String exchangeId = exchange.getExchangeId();
        String routeId = exchange.getProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, String.class);
        if (routeId == null) {
            routeId = exchange.getFromRouteId();
        }
        String endpointUri = exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT, String.class);
        String exceptionType = exception.getClass().getName();
        String exceptionMessage = exception.getMessage();
        String[] stackTrace = stackTraceEnabled ? captureStackTrace(exception) : null;

        DefaultErrorRegistryEntry entry = new DefaultErrorRegistryEntry(
                exchangeId, routeId, endpointUri, Instant.now(),
                handled, exceptionType, exceptionMessage, stackTrace);

        entries.addFirst(entry);
        evict();
    }

    private static String[] captureStackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer, true));
        return writer.toString().split("\n");
    }

    private void evict() {
        // remove excess entries beyond maximum
        while (entries.size() > maximumEntries) {
            entries.pollLast();
        }
        // remove expired entries from the tail (oldest)
        Instant cutoff = Instant.now().minus(timeToLive);
        while (!entries.isEmpty()) {
            ErrorRegistryEntry last = entries.peekLast();
            if (last != null && last.timestamp().isBefore(cutoff)) {
                entries.pollLast();
            } else {
                break;
            }
        }
    }

    // -- View methods (global scope) --

    @Override
    public int size() {
        evict();
        return entries.size();
    }

    @Override
    public Collection<ErrorRegistryEntry> browse() {
        return browse(-1);
    }

    @Override
    public Collection<ErrorRegistryEntry> browse(int limit) {
        evict();
        if (limit <= 0) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
        List<ErrorRegistryEntry> result = new ArrayList<>(Math.min(limit, entries.size()));
        int count = 0;
        for (ErrorRegistryEntry entry : entries) {
            if (count >= limit) {
                break;
            }
            result.add(entry);
            count++;
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    // -- Scoped view --

    @Override
    public ErrorRegistryView forRoute(String routeId) {
        return new RouteView(routeId);
    }

    // -- Configuration --

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int getMaximumEntries() {
        return maximumEntries;
    }

    @Override
    public void setMaximumEntries(int maximumEntries) {
        this.maximumEntries = maximumEntries;
    }

    @Override
    public Duration getTimeToLive() {
        return timeToLive;
    }

    @Override
    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    @Override
    public boolean isStackTraceEnabled() {
        return stackTraceEnabled;
    }

    @Override
    public void setStackTraceEnabled(boolean stackTraceEnabled) {
        this.stackTraceEnabled = stackTraceEnabled;
    }

    /**
     * A filtered view over entries for a specific route.
     */
    private class RouteView implements ErrorRegistryView {

        private final String routeId;

        RouteView(String routeId) {
            this.routeId = Objects.requireNonNull(routeId);
        }

        @Override
        public int size() {
            evict();
            int count = 0;
            for (ErrorRegistryEntry entry : entries) {
                if (routeId.equals(entry.routeId())) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Collection<ErrorRegistryEntry> browse() {
            return browse(-1);
        }

        @Override
        public Collection<ErrorRegistryEntry> browse(int limit) {
            evict();
            List<ErrorRegistryEntry> result = new ArrayList<>();
            for (ErrorRegistryEntry entry : entries) {
                if (routeId.equals(entry.routeId())) {
                    result.add(entry);
                    if (limit > 0 && result.size() >= limit) {
                        break;
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public void clear() {
            entries.removeIf(entry -> routeId.equals(entry.routeId()));
        }
    }

    /**
     * Immutable snapshot of an error.
     */
    private record DefaultErrorRegistryEntry(
            String exchangeId,
            String routeId,
            String endpointUri,
            Instant timestamp,
            boolean handled,
            String exceptionType,
            String exceptionMessage,
            String[] stackTrace)
            implements
                ErrorRegistryEntry {
    }
}
