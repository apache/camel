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
package org.apache.camel.tracing;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Utility class for managing active spans as a stack associated with an exchange.
 */
public final class ActiveSpanManager {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";
    private static final String ACTIVE_SPAN_PROPERTY = "OpenTracing.activeSpan";
    private static final Logger LOG = LoggerFactory.getLogger(ActiveSpanManager.class);

    private ActiveSpanManager() {
    }

    /**
     * This method returns the current active span associated with the exchange.
     *
     * @param  exchange The exchange
     * @return          The current active span, or null if none exists
     */
    public static SpanAdapter getSpan(Exchange exchange) {
        Holder holder = (Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
        if (holder != null) {
            return holder.getSpan();
        }
        return null;
    }

    /**
     * This method activates the supplied span for the supplied exchange. If an existing span is found for the exchange,
     * this will be pushed onto a stack.
     *
     * @param exchange The exchange
     * @param span     The span
     */
    public static void activate(Exchange exchange, SpanAdapter span) {
        exchange.setProperty(ACTIVE_SPAN_PROPERTY,
                new Holder((Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY), span));
        if (exchange.getContext().isUseMDCLogging()) {
            MDC.put(MDC_TRACE_ID, span.traceId());
            MDC.put(MDC_SPAN_ID, span.spanId());
        }
    }

    /**
     * This method deactivates an existing active span associated with the supplied exchange. Once deactivated, if a
     * parent span is found associated with the stack for the exchange, it will be restored as the current span for that
     * exchange.
     *
     * @param exchange The exchange
     */
    public static void deactivate(Exchange exchange) {
        Holder holder = (Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
        if (holder != null) {
            exchange.setProperty(ACTIVE_SPAN_PROPERTY, holder.getParent());

            holder.closeScope();
            if (exchange.getContext().isUseMDCLogging()) {
                Holder parent = holder.getParent();
                if (parent != null) {
                    SpanAdapter span = holder.getParent().getSpan();
                    MDC.put(MDC_TRACE_ID, span.traceId());
                    MDC.put(MDC_SPAN_ID, span.spanId());
                } else {
                    MDC.remove(MDC_TRACE_ID);
                    MDC.remove(MDC_SPAN_ID);
                }
            }
        }
    }

    /**
     * If underlying span is active, closes its scope without ending the span. This methods should be called after async
     * execution is started on the same thread on which span was activated. ExchangeAsyncStartedEvent is used to notify
     * about it.
     *
     * @param exchange The exchange
     */
    public static void endScope(Exchange exchange) {
        Holder holder = (Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
        if (holder != null) {
            holder.closeScope();
        }
    }

    /**
     * Simple holder for the currently active span and an optional reference to the parent holder. This will be used to
     * maintain a stack for spans, built up during the execution of a series of chained camel exchanges, and then
     * unwound when the responses are processed.
     *
     */
    public static class Holder {
        private Holder parent;
        private SpanAdapter span;
        private AutoCloseable scope;

        public Holder(Holder parent, SpanAdapter span) {
            this.parent = parent;
            this.span = span;
            this.scope = span.makeCurrent();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Tracing: started scope={}", this.scope);
            }
        }

        public Holder getParent() {
            return parent;
        }

        public SpanAdapter getSpan() {
            return span;
        }

        private void closeScope() {
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Tracing: closing scope={}", this.scope);
                }
                scope.close();
            } catch (Exception e) {
                LOG.debug("Failed to close span scope", e);
            }
        }
    }
}
