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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for {@link org.apache.camel.spi.UnitOfWork}
 */
public final class UnitOfWorkHelper {

    private static final Logger LOG = LoggerFactory.getLogger(UnitOfWorkHelper.class);

    private UnitOfWorkHelper() {
    }

    /**
     * Done and stop the {@link UnitOfWork}.
     *
     * @param uow      the unit of work
     * @param exchange the exchange (will unset the UoW on the exchange)
     */
    public static void doneUow(UnitOfWork uow, Exchange exchange) {
        if (uow == null) {
            return;
        }
        // unit of work is done
        try {
            uow.done(exchange);
        } catch (Exception e) {
            LOG.warn("Exception occurred during done UnitOfWork for Exchange: {}. This exception will be ignored.",
                    exchange.getExchangeId(), e);
        }
    }

    public static void doneSynchronizations(Exchange exchange, List<Synchronization> synchronizations) {
        if (synchronizations == null || synchronizations.isEmpty()) {
            return;
        }

        if (synchronizations.size() > 1) {
            // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
            final List<Synchronization> copy = safeCopy(synchronizations);

            boolean failed = exchange.isFailed();

            // invoke synchronization callbacks
            for (Synchronization synchronization : copy) {
                doneSynchronization(synchronization, exchange, failed);
            }
        } else {
            // there are only 1 synchronization to done
            doneSynchronization(synchronizations.get(0), exchange, exchange.isFailed());
        }
    }

    private static void doneSynchronization(Synchronization synchronization, Exchange exchange, boolean failed) {
        try {
            if (failed) {
                LOG.trace("Invoking synchronization.onFailure: {} with {}", synchronization, exchange);
                synchronization.onFailure(exchange);
            } else {
                LOG.trace("Invoking synchronization.onComplete: {} with {}", synchronization, exchange);
                synchronization.onComplete(exchange);
            }
        } catch (Exception e) {
            // must catch exceptions to ensure all synchronizations have a chance to run
            LOG.warn("Exception occurred during onCompletion. This exception will be ignored.", e);
        }
    }

    public static void beforeRouteSynchronizations(
            Route route, Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
        final List<Synchronization> copy = safeCopy(synchronizations);

        // invoke synchronization callbacks
        invokeSynchronizationCallbacks(route, exchange, log, copy);
    }

    private static List<Synchronization> safeCopy(List<Synchronization> synchronizations) {
        List<Synchronization> copy = new ArrayList<>(synchronizations);

        if (copy.size() > 1) {
            // reverse so we invoke it FILO style instead of FIFO
            Collections.reverse(copy);
            // and honor if any was ordered by sorting it accordingly
            copy.sort(OrderedComparator.get());
        }
        return copy;
    }

    private static void invokeSynchronizationCallbacks(Route route, Exchange exchange, Logger log, List<Synchronization> copy) {
        for (Synchronization synchronization : copy) {
            final SynchronizationRouteAware routeSynchronization = synchronization.getRouteSynchronization();
            if (routeSynchronization != null) {
                try {
                    log.trace("Invoking synchronization.onBeforeRoute: {} with {}", synchronization, exchange);
                    routeSynchronization.onBeforeRoute(route, exchange);
                } catch (Exception e) {
                    // must catch exceptions to ensure all synchronizations have a chance to run
                    log.warn("Exception occurred during onBeforeRoute. This exception will be ignored.", e);
                }
            }
        }
    }

    public static void afterRouteSynchronizations(
            Route route, Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
        final List<Synchronization> copy = safeCopy(synchronizations);

        // invoke synchronization callbacks
        for (Synchronization synchronization : copy) {
            final SynchronizationRouteAware routeSynchronization = synchronization.getRouteSynchronization();
            if (routeSynchronization != null) {
                try {
                    log.trace("Invoking synchronization.onAfterRoute: {} with {}", synchronization, exchange);
                    routeSynchronization.onAfterRoute(route, exchange);
                } catch (Exception e) {
                    // must catch exceptions to ensure all synchronizations have a chance to run
                    log.warn("Exception occurred during onAfterRoute. This exception will be ignored.", e);
                }
            }
        }
    }

}
