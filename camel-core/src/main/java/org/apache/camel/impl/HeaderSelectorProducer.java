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
package org.apache.camel.impl;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.InvokeOnHeaders;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A selector-based produced which uses an header value to determine which processor
 * should be invoked.
 */
public class HeaderSelectorProducer extends BaseSelectorProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderSelectorProducer.class);

    private final Supplier<String> headerSupplier;
    private final Supplier<String> defaultHeaderValueSupplier;
    private final Object target;
    private Map<String, Processor> handlers;

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier) {
        this(endpoint, headerSupplier, () -> null, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, boolean caseSensitive) {
        this(endpoint, headerSupplier, () -> null, null, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header) {
        this(endpoint, () -> header, () -> null, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, boolean caseSensitive) {
        this(endpoint, () -> header, () -> null, null, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Object target) {
        this(endpoint, () -> header, () -> null, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Object target, boolean caseSensitive) {
        this(endpoint, () -> header, () -> null, target, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint,  Supplier<String> headerSupplier, Object target) {
        this(endpoint, headerSupplier, () -> null, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint,  Supplier<String> headerSupplier, Object target, boolean caseSensitive) {
        this(endpoint, headerSupplier, () -> null, target, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue) {
        this(endpoint, () -> header, () -> defaultHeaderValue, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue, boolean caseSensitive) {
        this(endpoint, () -> header, () -> defaultHeaderValue, null, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Supplier<String> defaultHeaderValueSupplier) {
        this(endpoint, () -> header, defaultHeaderValueSupplier, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Supplier<String> defaultHeaderValueSupplier, boolean caseSensitive) {
        this(endpoint, () -> header, defaultHeaderValueSupplier, null, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, Supplier<String> defaultHeaderValueSupplier) {
        this(endpoint, headerSupplier, defaultHeaderValueSupplier, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, Supplier<String> defaultHeaderValueSupplier, boolean caseSensitive) {
        this(endpoint, headerSupplier, defaultHeaderValueSupplier, null, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue, Object target) {
        this(endpoint, () -> header, () -> defaultHeaderValue, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue, Object target, boolean caseSensitive) {
        this(endpoint, () -> header, () -> defaultHeaderValue, target, caseSensitive);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, Supplier<String> defaultHeaderValueSupplier, Object target) {
        this(endpoint, headerSupplier, defaultHeaderValueSupplier, target, true);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, Supplier<String> defaultHeaderValueSupplier, Object target, boolean caseSensitive) {
        super(endpoint);

        this.headerSupplier = ObjectHelper.notNull(headerSupplier, "headerSupplier");
        this.defaultHeaderValueSupplier = ObjectHelper.notNull(defaultHeaderValueSupplier, "defaultHeaderValueSupplier");
        this.target = target != null ? target : this;
        this.handlers = caseSensitive ?  new HashMap<>() : new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    protected void doStart() throws Exception {
        for (final Method method : target.getClass().getDeclaredMethods()) {
            InvokeOnHeaders annotation = method.getAnnotation(InvokeOnHeaders.class);
            if (annotation != null) {
                for (InvokeOnHeader processor : annotation.value()) {
                    bind(processor, method);
                }
            } else {
                bind(method.getAnnotation(InvokeOnHeader.class), method);
            }
        }

        handlers = Collections.unmodifiableMap(handlers);

        super.doStart();
    }

    @Override
    protected Processor getProcessor(Exchange exchange) throws Exception {
        String header = headerSupplier.get();
        String action = exchange.getIn().getHeader(header, String.class);

        if (action == null) {
            action = defaultHeaderValueSupplier.get();
        }
        if (action == null) {
            throw new NoSuchHeaderException(exchange, header, String.class);
        }

        return handlers.get(action);
    }

    protected void onMissingProcessor(Exchange exchange) throws Exception {
        throw new IllegalStateException(
            "Unsupported operation " + exchange.getIn().getHeader(headerSupplier.get())
        );
    }

    protected final void bind(String key, Processor processor) {
        if (handlers.containsKey(key)) {
            LOGGER.warn("A processor is already set for action {}", key);
        }

        this.handlers.put(key, processor);
    }

    private void bind(InvokeOnHeader handler, final Method method) {
        if (handler != null && method.getParameterCount() == 1) {
            method.setAccessible(true);

            final Class<?> type = method.getParameterTypes()[0];

            LOGGER.debug("bind key={}, class={}, method={}, type={}",
                handler.value(), this.getClass(), method.getName(), type);

            if (Message.class.isAssignableFrom(type)) {
                bind(handler.value(), e -> method.invoke(target, e.getIn()));
            } else {
                bind(handler.value(), e -> method.invoke(target, e));
            }
        }
    }
}
