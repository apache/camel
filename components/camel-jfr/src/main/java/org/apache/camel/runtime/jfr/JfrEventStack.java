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
package org.apache.camel.runtime.jfr;

import jdk.jfr.Event;
import org.apache.camel.Exchange;

/**
 * Immutable single-linked stack of in-flight JFR events, held as an exchange property.
 * <p>
 * Copying an exchange (multicast, split, wireTap, ...) copies the property <i>map</i> but shares the property
 * <i>values</i>, so a mutable collection stored here would be pushed to and popped from concurrently by sibling
 * exchanges. Instead every push/pop replaces the property value, which only ever writes into the map owned by that one
 * exchange. This mirrors {@code SpanStorageManagerExchange} in camel-telemetry.
 *
 * @since 4.22
 */
final class JfrEventStack {

    private final Event event;
    private final JfrEventStack parent;

    private JfrEventStack(Event event, JfrEventStack parent) {
        this.event = event;
        this.parent = parent;
    }

    /**
     * Pushes an event onto the stack held under the given exchange property.
     */
    static void push(Exchange exchange, String property, Event event) {
        JfrEventStack current = exchange.getProperty(property, JfrEventStack.class);
        exchange.setProperty(property, new JfrEventStack(event, current));
    }

    /**
     * Pops the most recently pushed event, or {@code null} if the stack is empty.
     */
    static <T extends Event> T pop(Exchange exchange, String property, Class<T> type) {
        JfrEventStack current = exchange.getProperty(property, JfrEventStack.class);
        if (current == null) {
            return null;
        }
        if (current.parent != null) {
            exchange.setProperty(property, current.parent);
        } else {
            exchange.removeProperty(property);
        }
        return type.cast(current.event);
    }
}
