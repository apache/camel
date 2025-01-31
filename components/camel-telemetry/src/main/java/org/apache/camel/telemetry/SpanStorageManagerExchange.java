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
package org.apache.camel.telemetry;

import org.apache.camel.Exchange;

/**
 * Storage hierarchy of Spans directly in Camel Exchanges.
 */
public class SpanStorageManagerExchange implements SpanStorageManager {

    protected static String ACTIVE_SPAN = "tracing.activeSpan";

    @Override
    public Span peek(Exchange exchange) {
        final SpanWrapper wrap = exchange.getProperty(ACTIVE_SPAN, SpanWrapper.class);
        if (wrap == null) {
            return null;
        }

        return wrap.current;
    }

    @Override
    public Span pull(Exchange exchange) {
        final SpanWrapper wrap = exchange.getProperty(ACTIVE_SPAN, SpanWrapper.class);
        if (wrap == null) {
            return null;
        }
        // TODO add a core method to cast and remove from the map directly
        exchange.removeProperty(ACTIVE_SPAN);
        if (wrap.parent != null) {
            exchange.setProperty(ACTIVE_SPAN, wrap.parent);
        }

        return wrap.current;
    }

    @Override
    public void push(Exchange exchange, final Span span) {
        final SpanWrapper parentWrap = exchange.getProperty(ACTIVE_SPAN, SpanWrapper.class);
        final SpanWrapper currentWrap = new SpanWrapper(span, parentWrap);
        exchange.setProperty(ACTIVE_SPAN, currentWrap);
    }

    class SpanWrapper {
        private Span current;
        private SpanWrapper parent;

        public SpanWrapper(Span current, SpanWrapper parent) {
            this.current = current;
            this.parent = parent;
        }
    }

}
