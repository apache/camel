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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.OnCompletionListener;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the {@link OnCompletionListener}s, for the places in Camel that change or run the on completions of an
 * exchange. A place gets the listeners with {@link #getListeners(CamelContext)} and calls them only when the list is
 * not empty, so with no listener installed it only checks that the list is empty. What a listener throws is caught and
 * logged at WARN once per listener (then at DEBUG), so it cannot change the routing.
 * <p/>
 * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
 *
 * @since 4.24
 */
public final class OnCompletionListenerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(OnCompletionListenerHelper.class);

    // the listeners that already failed once, so they are logged at WARN once (weak keys: a listener is not kept)
    private static final Map<OnCompletionListener, Boolean> FAILED_LISTENERS
            = Collections.synchronizedMap(new WeakHashMap<>());

    private OnCompletionListenerHelper() {
    }

    /**
     * The listeners installed on the context.
     *
     * @return the listeners (a view, not a copy), or an empty list if none is installed or there is no context
     */
    public static List<OnCompletionListener> getListeners(CamelContext context) {
        final ExtendedCamelContext ecc = context != null ? context.getCamelContextExtension() : null;
        return ecc != null ? ecc.getOnCompletionListeners() : Collections.emptyList();
    }

    public static void onUnitOfWork(
            List<OnCompletionListener> listeners, Exchange exchange, UnitOfWork unitOfWork, List<Synchronization> moved) {
        final List<Synchronization> view = Collections.unmodifiableList(moved);
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onUnitOfWork(exchange, unitOfWork, view);
            } catch (Throwable e) {
                failed(listener, "onUnitOfWork", e);
            }
        }
    }

    public static void onCompletionAdded(
            List<OnCompletionListener> listeners, UnitOfWork unitOfWork, Synchronization onCompletion) {
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onCompletionAdded(unitOfWork, onCompletion);
            } catch (Throwable e) {
                failed(listener, "onCompletionAdded", e);
            }
        }
    }

    public static void onCompletionRemoved(
            List<OnCompletionListener> listeners, UnitOfWork unitOfWork, Synchronization onCompletion) {
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onCompletionRemoved(unitOfWork, onCompletion);
            } catch (Throwable e) {
                failed(listener, "onCompletionRemoved", e);
            }
        }
    }

    public static void onCompletionHandover(
            List<OnCompletionListener> listeners, Exchange from, UnitOfWork fromUnitOfWork, Exchange to,
            List<Synchronization> moved) {
        final List<Synchronization> view = moved != null ? Collections.unmodifiableList(moved) : null;
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onCompletionHandover(from, fromUnitOfWork, to, view);
            } catch (Throwable e) {
                failed(listener, "onCompletionHandover", e);
            }
        }
    }

    public static void onCompletionTaken(List<OnCompletionListener> listeners, Exchange from, List<Synchronization> taken) {
        final List<Synchronization> view = Collections.unmodifiableList(taken);
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onCompletionTaken(from, view);
            } catch (Throwable e) {
                failed(listener, "onCompletionTaken", e);
            }
        }
    }

    public static void onCompletionRun(
            List<OnCompletionListener> listeners, Exchange exchange, Synchronization onCompletion, boolean failure) {
        for (OnCompletionListener listener : listeners) {
            try {
                listener.onCompletionRun(exchange, onCompletion, failure);
            } catch (Throwable e) {
                failed(listener, "onCompletionRun", e);
            }
        }
    }

    private static void failed(OnCompletionListener listener, String method, Throwable cause) {
        try {
            if (FAILED_LISTENERS.put(listener, Boolean.TRUE) == null) {
                LOG.warn("OnCompletionListener {} failed in {} (logged once for this listener, then at DEBUG)", listener,
                        method, cause);
            } else {
                LOG.debug("OnCompletionListener {} failed in {}", listener, method, cause);
            }
        } catch (Throwable t) {
            // a listener cannot change the routing, also when logging it fails (such as its toString)
        }
    }
}
