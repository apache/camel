/**
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
package org.apache.camel.opentracing;

import io.opentracing.Span;

import org.apache.camel.Exchange;

/**
 * Utility class for managing active spans as a stack associated with
 * an exchange.
 *
 */
public final class ActiveSpanManager {

    private static final String ACTIVE_SPAN_PROPERTY = "OpenTracing.activeSpan";

    private ActiveSpanManager() {
    }

    /**
     * This method returns the current active span associated with the
     * exchange.
     *
     * @param exchange The exchange
     * @return The current active span, or null if none exists
     */
    public static Span getSpan(Exchange exchange) {
        Holder holder = (Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
        if (holder != null) {
            return holder.getSpan();
        }
        return null;
    }

    /**
     * This method activates the supplied span for the supplied exchange.
     * If an existing span is found for the exchange, this will be pushed
     * onto a stack.
     *
     * @param exchange The exchange
     * @param span The span
     */
    public static void activate(Exchange exchange, Span span) {
        exchange.setProperty(ACTIVE_SPAN_PROPERTY,
                new Holder((Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY), span));
    }

    /**
     * This method deactivates an existing active span associated with the
     * supplied exchange. Once deactivated, if a parent span is found
     * associated with the stack for the exchange, it will be restored
     * as the current span for that exchange.
     *
     * @param exchange The exchange
     */
    public static void deactivate(Exchange exchange) {
        Holder holder = (Holder) exchange.getProperty(ACTIVE_SPAN_PROPERTY);
        if (holder != null) {
            exchange.setProperty(ACTIVE_SPAN_PROPERTY, holder.getParent());
        }
    }

    /**
     * Simple holder for the currently active span and an optional reference to
     * the parent holder. This will be used to maintain a stack for spans, built
     * up during the execution of a series of chained camel exchanges, and then
     * unwound when the responses are processed.
     *
     */
    public static class Holder {
        private Holder parent;
        private Span span;
    
        public Holder(Holder parent, Span span) {
            this.parent = parent;
            this.span = span;
        }
    
        public Holder getParent() {
            return parent;
        }
    
        public Span getSpan() {
            return span;
        }
    }
}
