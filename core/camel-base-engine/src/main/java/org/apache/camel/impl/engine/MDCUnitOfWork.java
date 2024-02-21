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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.PatternHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This unit of work supports <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a>.
 */
public class MDCUnitOfWork extends DefaultUnitOfWork implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(MDCUnitOfWork.class);

    private final String pattern;

    private final String originalBreadcrumbId;
    private final String originalExchangeId;
    private final String originalMessageId;
    private final String originalCorrelationId;
    private final String originalRouteId;
    private final String originalStepId;
    private final String originalCamelContextId;
    private final String originalTransactionKey;

    public MDCUnitOfWork(Exchange exchange, InflightRepository inflightRepository,
                         String pattern, boolean allowUseOriginalMessage, boolean useBreadcrumb) {
        super(exchange, LOG, inflightRepository, allowUseOriginalMessage, useBreadcrumb);
        this.pattern = pattern;

        // remember existing values
        this.originalExchangeId = MDC.get(MDC_EXCHANGE_ID);
        this.originalMessageId = MDC.get(MDC_MESSAGE_ID);
        this.originalBreadcrumbId = MDC.get(MDC_BREADCRUMB_ID);
        this.originalCorrelationId = MDC.get(MDC_CORRELATION_ID);
        this.originalRouteId = MDC.get(MDC_ROUTE_ID);
        this.originalStepId = MDC.get(MDC_STEP_ID);
        this.originalCamelContextId = MDC.get(MDC_CAMEL_CONTEXT_ID);
        this.originalTransactionKey = MDC.get(MDC_TRANSACTION_KEY);

        prepareMDC(exchange);
    }

    protected void prepareMDC(Exchange exchange) {
        // must add exchange and message id in constructor
        MDC.put(MDC_EXCHANGE_ID, exchange.getExchangeId());
        String msgId = exchange.getMessage().getMessageId();
        MDC.put(MDC_MESSAGE_ID, msgId);
        // the camel context id is from exchange
        MDC.put(MDC_CAMEL_CONTEXT_ID, exchange.getContext().getName());
        // and add optional correlation id
        String corrId = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
        if (corrId != null) {
            MDC.put(MDC_CORRELATION_ID, corrId);
        }
        // and add optional breadcrumb id
        String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
        if (breadcrumbId != null) {
            MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
        }
        Route current = getRoute();
        if (current != null) {
            MDC.put(MDC_ROUTE_ID, current.getRouteId());
        }
    }

    @Override
    public UnitOfWork newInstance(Exchange exchange) {
        return new MDCUnitOfWork(exchange, inflightRepository, pattern, allowUseOriginalMessage, useBreadcrumb);
    }

    @Override
    public void pushRoute(Route route) {
        super.pushRoute(route);
        if (route != null) {
            MDC.put(MDC_ROUTE_ID, route.getRouteId());
        } else {
            MDC.remove(MDC_ROUTE_ID);
        }
    }

    @Override
    public Route popRoute() {
        Route answer = super.popRoute();
        // restore old route id back again after we have popped
        Route previous = getRoute();
        if (previous != null) {
            // restore old route id back again
            MDC.put(MDC_ROUTE_ID, previous.getRouteId());
        } else {
            // not running in route, so clear (should ideally not happen)
            MDC.remove(MDC_ROUTE_ID);
        }

        return answer;
    }

    @Override
    public void beginTransactedBy(Object key) {
        MDC.put(MDC_TRANSACTION_KEY, key.toString());
        super.beginTransactedBy(key);
    }

    @Override
    public void endTransactedBy(Object key) {
        MDC.remove(MDC_TRANSACTION_KEY);
        super.endTransactedBy(key);
    }

    @Override
    public boolean isBeforeAfterProcess() {
        return true;
    }

    @Override
    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        // prepare MDC before processing
        prepareMDC(exchange);
        // add optional step id
        String stepId = exchange.getProperty(ExchangePropertyKey.STEP_ID, String.class);
        if (stepId != null) {
            MDC.put(MDC_STEP_ID, stepId);
        }
        // return callback with after processing work
        final AsyncCallback uowCallback = super.beforeProcess(processor, exchange, callback);
        return new MDCCallback(uowCallback, pattern);
    }

    @Override
    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        // if we are no longer under step then remove it
        String stepId = exchange.getProperty(ExchangePropertyKey.STEP_ID, String.class);
        if (stepId == null) {
            MDC.remove(MDC_STEP_ID);
        }
        // clear MDC to avoid leaking to current thread when
        // the exchange is continued routed asynchronously
        clear();
    }

    /**
     * Clears information put on the MDC by this {@link MDCUnitOfWork}
     */
    public void clear() {
        if (this.originalBreadcrumbId != null) {
            MDC.put(MDC_BREADCRUMB_ID, originalBreadcrumbId);
        } else {
            MDC.remove(MDC_BREADCRUMB_ID);
        }
        if (this.originalExchangeId != null) {
            MDC.put(MDC_EXCHANGE_ID, originalExchangeId);
        } else {
            MDC.remove(MDC_EXCHANGE_ID);
        }
        if (this.originalMessageId != null) {
            MDC.put(MDC_MESSAGE_ID, originalMessageId);
        } else {
            MDC.remove(MDC_MESSAGE_ID);
        }
        if (this.originalCorrelationId != null) {
            MDC.put(MDC_CORRELATION_ID, originalCorrelationId);
        } else {
            MDC.remove(MDC_CORRELATION_ID);
        }
        if (this.originalRouteId != null) {
            MDC.put(MDC_ROUTE_ID, originalRouteId);
        } else {
            MDC.remove(MDC_ROUTE_ID);
        }
        if (this.originalStepId != null) {
            MDC.put(MDC_STEP_ID, originalStepId);
        } else {
            MDC.remove(MDC_STEP_ID);
        }
        if (this.originalCamelContextId != null) {
            MDC.put(MDC_CAMEL_CONTEXT_ID, originalCamelContextId);
        } else {
            MDC.remove(MDC_CAMEL_CONTEXT_ID);
        }
        if (this.originalTransactionKey != null) {
            MDC.put(MDC_TRANSACTION_KEY, originalTransactionKey);
        } else {
            MDC.remove(MDC_TRANSACTION_KEY);
        }
    }

    /**
     * Clear custom MDC values based on the configured MDC pattern
     */
    protected void clearCustom(Exchange exchange) {
        // clear custom patterns
        if (pattern != null) {

            // only clear if the UoW is the parent UoW (split, multicast and other EIPs create child exchanges with their own UoW)
            if (exchange != null) {
                String cid = exchange.getProperty(ExchangePropertyKey.CORRELATION_ID, String.class);
                if (cid != null && !cid.equals(exchange.getExchangeId())) {
                    return;
                }
            }

            Map<String, String> mdc = MDC.getCopyOfContextMap();
            if (mdc != null) {
                if ("*".equals(pattern)) {
                    MDC.clear();
                } else {
                    final String[] patterns = pattern.split(",");
                    mdc.forEach((k, v) -> {
                        if (PatternHelper.matchPatterns(k, patterns)) {
                            MDC.remove(k);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void done(Exchange exchange) {
        super.done(exchange);
        // clear custom first
        clearCustom(exchange);
        clear();
    }

    @Override
    protected void onDone() {
        super.onDone();
        // clear MDC, so we do not leak as Camel is done using this UoW
        clear();
    }

    @Override
    public void reset() {
        super.reset();
        // clear custom first
        clearCustom(null);
        clear();
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        clear();
    }

    @Override
    public String toString() {
        return "MDCUnitOfWork";
    }

    /**
     * {@link AsyncCallback} which preserves {@link org.slf4j.MDC} when the asynchronous routing engine is being used.
     */
    private static final class MDCCallback implements AsyncCallback {

        private final AsyncCallback delegate;
        private final String breadcrumbId;
        private final String exchangeId;
        private final String messageId;
        private final String correlationId;
        private final String routeId;
        private final String camelContextId;
        private final Map<String, String> custom;

        private MDCCallback(AsyncCallback delegate, String pattern) {
            this.delegate = delegate;
            this.exchangeId = MDC.get(MDC_EXCHANGE_ID);
            this.messageId = MDC.get(MDC_MESSAGE_ID);
            this.breadcrumbId = MDC.get(MDC_BREADCRUMB_ID);
            this.correlationId = MDC.get(MDC_CORRELATION_ID);
            this.camelContextId = MDC.get(MDC_CAMEL_CONTEXT_ID);
            this.routeId = MDC.get(MDC_ROUTE_ID);

            if (pattern != null) {
                custom = new HashMap<>();
                Map<String, String> mdc = MDC.getCopyOfContextMap();
                if (mdc != null) {
                    if ("*".equals(pattern)) {
                        custom.putAll(mdc);
                    } else {
                        final String[] patterns = pattern.split(",");
                        mdc.forEach((k, v) -> {
                            if (PatternHelper.matchPatterns(k, patterns)) {
                                custom.put(k, v);
                            }
                        });
                    }
                }
            } else {
                custom = null;
            }
        }

        @Override
        public void done(boolean doneSync) {
            try {
                if (!doneSync) {
                    // when done asynchronously then restore information from previous thread
                    if (breadcrumbId != null) {
                        MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
                    }
                    if (exchangeId != null) {
                        MDC.put(MDC_EXCHANGE_ID, exchangeId);
                    }
                    if (messageId != null) {
                        MDC.put(MDC_MESSAGE_ID, messageId);
                    }
                    if (correlationId != null) {
                        MDC.put(MDC_CORRELATION_ID, correlationId);
                    }
                    if (camelContextId != null) {
                        MDC.put(MDC_CAMEL_CONTEXT_ID, camelContextId);
                    }
                    if (custom != null) {
                        // keep existing custom value to not override
                        custom.forEach((k, v) -> {
                            if (MDC.get(k) == null) {
                                MDC.put(k, v);
                            }
                        });
                    }
                }
                // need to setup the routeId finally
                if (routeId != null) {
                    MDC.put(MDC_ROUTE_ID, routeId);
                }

            } finally {
                // muse ensure delegate is invoked
                delegate.done(doneSync);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

}
