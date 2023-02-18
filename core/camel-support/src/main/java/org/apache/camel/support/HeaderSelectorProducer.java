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
package org.apache.camel.support;

import java.util.function.Supplier;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.spi.InvokeOnHeaderStrategy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A selector-based producer which uses a header value to determine which processor should be invoked.
 *
 * @see org.apache.camel.spi.InvokeOnHeader
 * @see InvokeOnHeaderStrategy
 */
public abstract class HeaderSelectorProducer extends DefaultAsyncProducer implements CamelContextAware {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/invoke-on-header/";

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderSelectorProducer.class);

    private final Supplier<String> headerSupplier;
    private final Supplier<String> defaultHeaderValueSupplier;
    private final Object target;
    private CamelContext camelContext;
    private InvokeOnHeaderStrategy strategy;
    private InvokeOnHeaderStrategy parentStrategy;

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier) {
        this(endpoint, headerSupplier, () -> null, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header) {
        this(endpoint, () -> header, () -> null, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Object target) {
        this(endpoint, () -> header, () -> null, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier, Object target) {
        this(endpoint, headerSupplier, () -> null, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue) {
        this(endpoint, () -> header, () -> defaultHeaderValue, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, Supplier<String> defaultHeaderValueSupplier) {
        this(endpoint, () -> header, defaultHeaderValueSupplier, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier,
                                  Supplier<String> defaultHeaderValueSupplier) {
        this(endpoint, headerSupplier, defaultHeaderValueSupplier, null);
    }

    public HeaderSelectorProducer(Endpoint endpoint, String header, String defaultHeaderValue, Object target) {
        this(endpoint, () -> header, () -> defaultHeaderValue, target);
    }

    public HeaderSelectorProducer(Endpoint endpoint, Supplier<String> headerSupplier,
                                  Supplier<String> defaultHeaderValueSupplier, Object target) {
        super(endpoint);

        this.headerSupplier = ObjectHelper.notNull(headerSupplier, "headerSupplier");
        this.defaultHeaderValueSupplier = ObjectHelper.notNull(defaultHeaderValueSupplier, "defaultHeaderValueSupplier");
        this.target = target != null ? target : this;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        String key = this.getClass().getName();
        String fqn = RESOURCE_PATH + key;
        strategy = camelContext.getCamelContextExtension().getBootstrapFactoryFinder(RESOURCE_PATH)
                .newInstance(key, InvokeOnHeaderStrategy.class)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find " + fqn + " in classpath."));

        Class<?> sclazz = this.getClass().getSuperclass();
        if (sclazz != null && !sclazz.getName().equals("java.lang.Object")
                && !sclazz.getName().equals(HeaderSelectorProducer.class.getName())) {
            // some components may have a common base class they extend from (such as camel-infinispan)
            // so try to discover that (optional so return null if not present)
            String key2 = this.getClass().getSuperclass().getName();
            parentStrategy = camelContext.getCamelContextExtension().getBootstrapFactoryFinder(RESOURCE_PATH)
                    .newInstance(key2, InvokeOnHeaderStrategy.class)
                    .orElse(null);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean sync = true;
        try {
            String header = headerSupplier.get();
            String action = exchange.getIn().getHeader(header, String.class);

            if (action == null) {
                action = defaultHeaderValueSupplier.get();
            }
            if (action == null) {
                throw new NoSuchHeaderException(exchange, header, String.class);
            }

            LOGGER.debug("Invoking @InvokeOnHeader method: {}", action);
            Object answer = strategy.invoke(target, action, exchange, callback);
            if (answer == null && parentStrategy != null) {
                answer = parentStrategy.invoke(target, action, exchange, callback);
            }
            if (answer == callback) {
                // okay it was an async invoked so we should return false
                sync = false;
                answer = null;
            }
            if (sync) {
                LOGGER.trace("Invoked @InvokeOnHeader method: {} -> {}", action, answer);
                processResult(exchange, answer);
            } else {
                LOGGER.trace("Invoked @InvokeOnHeader method: {} is continuing asynchronously", action);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (sync) {
            // callback was not in use, so we must done it here
            callback.done(true);
        }
        return sync;
    }

    /**
     * Process the result. Will by default set the result as the message body.
     *
     * @param exchange the exchange
     * @param result   the result (may be null)
     */
    protected void processResult(Exchange exchange, Object result) {
        if (result != null) {
            exchange.getMessage().setBody(result);
        }
    }

}
