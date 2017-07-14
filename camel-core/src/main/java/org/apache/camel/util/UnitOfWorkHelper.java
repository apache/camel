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
package org.apache.camel.util;

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
 *
 * @version 
 */
public final class UnitOfWorkHelper {

    private static final Logger LOG = LoggerFactory.getLogger(UnitOfWorkHelper.class);

    private UnitOfWorkHelper() {
    }

    /**
     * Creates a new {@link UnitOfWork}.
     *
     * @param exchange the exchange
     * @return the created unit of work (is not started)
     * @deprecated use {@link org.apache.camel.CamelContext#getUnitOfWorkFactory()} instead.
     */
    @Deprecated
    public static UnitOfWork createUoW(Exchange exchange) {
        return exchange.getContext().getUnitOfWorkFactory().createUnitOfWork(exchange);
    }

    /**
     * Done and stop the {@link UnitOfWork}.
     *
     * @param uow the unit of work
     * @param exchange the exchange (will unset the UoW on the exchange)
     */
    public static void doneUow(UnitOfWork uow, Exchange exchange) {
        // unit of work is done
        try {
            if (uow != null) {
                uow.done(exchange);
            }
        } catch (Throwable e) {
            LOG.warn("Exception occurred during done UnitOfWork for Exchange: " + exchange
                    + ". This exception will be ignored.", e);
        }
        try {
            if (uow != null) {
                uow.stop();
            }
        } catch (Throwable e) {
            LOG.warn("Exception occurred during stopping UnitOfWork for Exchange: " + exchange
                    + ". This exception will be ignored.", e);
        }

        // remove uow from exchange as its done
        exchange.setUnitOfWork(null);
    }

    public static void doneSynchronizations(Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        boolean failed = exchange.isFailed();

        if (synchronizations != null && !synchronizations.isEmpty()) {
            // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
            List<Synchronization> copy = new ArrayList<Synchronization>(synchronizations);

            // reverse so we invoke it FILO style instead of FIFO
            Collections.reverse(copy);
            // and honor if any was ordered by sorting it accordingly
            copy.sort(OrderedComparator.get());

            // invoke synchronization callbacks
            for (Synchronization synchronization : copy) {
                try {
                    if (failed) {
                        log.trace("Invoking synchronization.onFailure: {} with {}", synchronization, exchange);
                        synchronization.onFailure(exchange);
                    } else {
                        log.trace("Invoking synchronization.onComplete: {} with {}", synchronization, exchange);
                        synchronization.onComplete(exchange);
                    }
                } catch (Throwable e) {
                    // must catch exceptions to ensure all synchronizations have a chance to run
                    log.warn("Exception occurred during onCompletion. This exception will be ignored.", e);
                }
            }
        }
    }

    public static void beforeRouteSynchronizations(Route route, Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        if (synchronizations != null && !synchronizations.isEmpty()) {
            // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
            List<Synchronization> copy = new ArrayList<Synchronization>(synchronizations);

            // reverse so we invoke it FILO style instead of FIFO
            Collections.reverse(copy);
            // and honor if any was ordered by sorting it accordingly
            copy.sort(OrderedComparator.get());

            // invoke synchronization callbacks
            for (Synchronization synchronization : copy) {
                if (synchronization instanceof SynchronizationRouteAware) {
                    try {
                        log.trace("Invoking synchronization.onBeforeRoute: {} with {}", synchronization, exchange);
                        ((SynchronizationRouteAware) synchronization).onBeforeRoute(route, exchange);
                    } catch (Throwable e) {
                        // must catch exceptions to ensure all synchronizations have a chance to run
                        log.warn("Exception occurred during onBeforeRoute. This exception will be ignored.", e);
                    }
                }
            }
        }
    }

    public static void afterRouteSynchronizations(Route route, Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        if (synchronizations != null && !synchronizations.isEmpty()) {
            // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
            List<Synchronization> copy = new ArrayList<Synchronization>(synchronizations);

            // reverse so we invoke it FILO style instead of FIFO
            Collections.reverse(copy);
            // and honor if any was ordered by sorting it accordingly
            copy.sort(OrderedComparator.get());

            // invoke synchronization callbacks
            for (Synchronization synchronization : copy) {
                if (synchronization instanceof SynchronizationRouteAware) {
                    try {
                        log.trace("Invoking synchronization.onAfterRoute: {} with {}", synchronization, exchange);
                        ((SynchronizationRouteAware) synchronization).onAfterRoute(route, exchange);
                    } catch (Throwable e) {
                        // must catch exceptions to ensure all synchronizations have a chance to run
                        log.warn("Exception occurred during onAfterRoute. This exception will be ignored.", e);
                    }
                }
            }
        }
    }

}
