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
package org.apache.camel.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.apache.camel.Exchange;

/**
 * Utility class for managing active tracing scope as a stack associated with
 * an exchange. Should be a part of {@link ActiveSpanManager} but is a separate class to keep API backwards compatible.
 *
 */
final class ActiveScopeManager {

    private static final String ACTIVE_SCOPE_PROPERTY = "OpenTracing.activeScope";

    private ActiveScopeManager() {
    }

    /**
     * Activates the supplied span for the supplied tracer and adds the activated scope into the stack on exchange.
     *
     * @param exchange The exchange
     * @param span The span
     */
    static void activate(Exchange exchange, Span span, Tracer tracer) {
        Scope scope = tracer.activateSpan(span);
        exchange.setProperty(ACTIVE_SCOPE_PROPERTY,
                new Holder(exchange.getProperty(ACTIVE_SCOPE_PROPERTY, Holder.class), scope));
    }

    /**
     * Deactivates an existing active span scope associated with the
     * supplied exchange. Once deactivated, if a parent scope is found
     * associated with the stack for the exchange, it will be restored
     * as the current scope for that exchange.
     *
     * @param exchange The exchange
     */
    static void deactivate(Exchange exchange) {
        Holder holder = exchange.getProperty(ACTIVE_SCOPE_PROPERTY, Holder.class);
        if (holder != null) {
            holder.getScope().close();
            exchange.setProperty(ACTIVE_SCOPE_PROPERTY, holder.getParent());
        }
    }

    /**
     * Simple holder for the currently active scope and an optional reference to
     * the parent holder. This will be used to maintain a stack for scopes, built
     * up during the execution of a series of chained camel exchanges, and then
     * unwound when the responses are processed.
     */
    private static final class Holder {
        private final Holder parent;
        private final Scope scope;

        Holder(Holder parent, Scope scope) {
            this.parent = parent;
            this.scope = scope;
        }

        Holder getParent() {
            return parent;
        }

        Scope getScope() {
            return scope;
        }
    }
}
