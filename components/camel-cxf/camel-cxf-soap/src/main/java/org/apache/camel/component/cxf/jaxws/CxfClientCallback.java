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
package org.apache.camel.component.cxf.jaxws;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.cxf.endpoint.ClientCallback;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfClientCallback extends ClientCallback {
    private static final Logger LOG = LoggerFactory.getLogger(CxfClientCallback.class);

    private final AsyncCallback camelAsyncCallback;
    private final Exchange camelExchange;
    private final org.apache.cxf.message.Exchange cxfExchange;
    private final BindingOperationInfo boi;
    private final CxfEndpoint endpoint;
    // Guard against done(false) being called more than once: CXF may call handleException
    // after handleResponse throws (e.g. ClientImpl.onMessage catches and re-dispatches).
    private final AtomicBoolean doneFired = new AtomicBoolean();

    public CxfClientCallback(AsyncCallback callback,
                             Exchange camelExchange,
                             org.apache.cxf.message.Exchange cxfExchange,
                             BindingOperationInfo boi,
                             CxfEndpoint endpoint) {
        this.camelAsyncCallback = callback;
        this.camelExchange = camelExchange;
        this.cxfExchange = cxfExchange;
        this.boi = boi;
        this.endpoint = endpoint;
    }

    @Override
    public void handleResponse(Map<String, Object> ctx, Object[] res) {
        try {
            super.handleResponse(ctx, res);
        } finally {
            // for one way messages callback is already called in
            // process method of org.apache.camel.component.cxf.CxfProducer
            if (!boi.getOperationInfo().isOneWay()) {
                // always signal the Camel callback, even if cookie store or binding population throws,
                // to prevent the calling thread from hanging in DefaultAsyncProcessorAwaitManager
                try {
                    // add cookies to the cookie store; catch Throwable so an unexpected runtime failure
                    // does not prevent populateExchangeFromCxfResponse from running or done() from firing
                    if (endpoint.getCookieHandler() != null && cxfExchange.getInMessage() != null) {
                        try {
                            Map<String, List<String>> cxfHeaders
                                    = CastUtils.cast((Map<?, ?>) cxfExchange.getInMessage().get(CxfConstants.PROTOCOL_HEADERS));
                            endpoint.getCookieHandler().storeCookies(camelExchange, endpoint.getRequestUri(camelExchange),
                                    cxfHeaders);
                        } catch (Throwable e) {
                            LOG.error("Cannot store cookies", e);
                            camelExchange.setException(e);
                        }
                    }
                    // bind the CXF response to Camel exchange
                    try {
                        endpoint.getCxfBinding().populateExchangeFromCxfResponse(camelExchange, cxfExchange, ctx);
                    } catch (Throwable t) {
                        // preserve any exception set by the cookie store; record binding error as suppressed
                        Exception cur = camelExchange.getException();
                        if (cur != null && cur != t) {
                            cur.addSuppressed(t);
                        } else if (cur == null) {
                            camelExchange.setException(t);
                        }
                    }
                } finally {
                    signalDone();
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} calling handleResponse", Thread.currentThread().getName());
            }
        }
    }

    @Override
    public void handleException(Map<String, Object> ctx, Throwable ex) {
        // If done(false) was already signalled (e.g. handleResponse ran first and then CXF re-dispatched
        // to handleException after it threw), skip all exchange mutations: the exchange is already done.
        if (doneFired.get()) {
            return;
        }
        try {
            super.handleException(ctx, ex);
            // need to call the conduitSelector complete method to enable the fail over feature
            ConduitSelector conduitSelector = cxfExchange.get(ConduitSelector.class);
            if (conduitSelector != null) {
                conduitSelector.complete(cxfExchange);
                // For FailoverTargetSelector, conduitSelector.complete() runs the retry itself.
                // If failover succeeds, neither message will carry an exception — do not set one,
                // as the exchange should remain clean for the re-dispatch to succeed.
                // Only propagate an exception when a message explicitly carries one.
                Throwable resolved = null;
                if (cxfExchange.getOutMessage() != null) {
                    resolved = cxfExchange.getOutMessage().getContent(Exception.class);
                }
                if (resolved == null && cxfExchange.getInMessage() != null) {
                    resolved = cxfExchange.getInMessage().getContent(Exception.class);
                }
                if (resolved != null) {
                    camelExchange.setException(resolved);
                }
            } else {
                camelExchange.setException(ex);
            }
        } finally {
            // for one way messages callback is already called in
            // process method of org.apache.camel.component.cxf.CxfProducer
            if (!boi.getOperationInfo().isOneWay()) {
                // always signal the Camel callback, even if cookie store or binding population throws,
                // to prevent the calling thread from hanging in DefaultAsyncProcessorAwaitManager
                try {
                    // add cookies to the cookie store; catch Throwable so an unexpected runtime failure
                    // does not prevent populateExchangeFromCxfResponse from running or done() from firing;
                    // use suppressed to preserve the root transport/fault exception already on the exchange
                    if (endpoint.getCookieHandler() != null && cxfExchange.getInMessage() != null) {
                        try {
                            Map<String, List<String>> cxfHeaders
                                    = CastUtils.cast((Map<?, ?>) cxfExchange.getInMessage().get(CxfConstants.PROTOCOL_HEADERS));
                            endpoint.getCookieHandler().storeCookies(camelExchange, endpoint.getRequestUri(camelExchange),
                                    cxfHeaders);
                        } catch (Throwable e) {
                            LOG.error("Cannot store cookies", e);
                            Throwable root = camelExchange.getException();
                            if (root != null && root != e) {
                                root.addSuppressed(e);
                            } else if (root == null) {
                                camelExchange.setException(e);
                            }
                        }
                    }
                    // copy the context information
                    try {
                        endpoint.getCxfBinding().populateExchangeFromCxfResponse(camelExchange, cxfExchange, ctx);
                    } catch (Throwable t) {
                        // preserve the root transport/fault exception; record the binding error as suppressed;
                        // guard against self-suppression (cur == t) which throws IllegalArgumentException
                        Exception cur = camelExchange.getException();
                        if (cur != null && cur != t) {
                            cur.addSuppressed(t);
                        } else if (cur == null) {
                            camelExchange.setException(t);
                        }
                    }
                } finally {
                    signalDone();
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} calling handleException", Thread.currentThread().getName());
            }
        }
    }

    /**
     * Signals the Camel async callback exactly once. Guards against CXF calling both {@code handleResponse} and
     * {@code handleException} on the same callback instance (e.g. when {@code handleResponse} throws and
     * {@code ClientImpl.onMessage} re-dispatches).
     */
    private void signalDone() {
        if (doneFired.compareAndSet(false, true)) {
            camelAsyncCallback.done(false);
        } else {
            LOG.warn("done(false) already fired for this callback — duplicate call suppressed");
        }
    }

}
