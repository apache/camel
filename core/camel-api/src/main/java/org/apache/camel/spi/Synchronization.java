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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.jspecify.annotations.Nullable;

/**
 * Provides a hook for custom {@link org.apache.camel.Processor} or {@link org.apache.camel.Component} instances to
 * respond to completed or failed processing of an {@link Exchange} rather like Spring's
 * {@code org.springframework.transaction.support.TransactionSynchronization}
 * <p/>
 * <b>The commit of a consumer.</b> A consumer typically adds a synchronization to the exchange before it routes it, to
 * commit or roll back its message when the exchange is done (such as moving or deleting a file). It moves into the unit
 * of work that is created for the exchange. For such a commit the routing engine keeps this contract:
 * <ul>
 * <li>Each time it is added, exactly one of {@link #onComplete(Exchange)} or {@link #onFailure(Exchange)} is called,
 * once (unless it is removed with {@link UnitOfWork#removeSynchronization(Synchronization)}).</li>
 * <li>It runs where it is held at the end: when the unit of work it was added to is done, with the outcome of its
 * exchange; or, when it was handed over, where it was handed over to. An exchange that has no unit of work yet (such as
 * a copy waiting in a {@code seda} queue) holds its commits itself, and they move into the unit of work it gets.</li>
 * <li>It moves only by a handover made by the exchange its unit of work was created for:
 * {@link org.apache.camel.ExchangeExtension#handoverCompletions(Exchange)} moves it to a copy that is routed on its own
 * (such as an {@code InOnly} exchange sent to {@code seda}), and it runs when the copy is done, with the outcome of the
 * copy; {@link org.apache.camel.ExchangeExtension#handoverCompletions()} takes it out to be run by someone else (such
 * as the aggregator, the enrichers or {@code onCompletion}). A {@link SynchronizationVetoable} can refuse to be handed
 * over. The parts of a split, multicast or recipient list with {@code shareUnitOfWork} use the unit of work of their
 * parent, and do not hand over the commit of the parent.</li>
 * </ul>
 */
public interface Synchronization {

    /**
     * Called when the processing of the message exchange is complete
     *
     * @param exchange the exchange being processed
     */
    void onComplete(Exchange exchange);

    /**
     * Called when the processing of the message exchange has failed for some reason. The exception which caused the
     * problem is in {@link Exchange#getException()}.
     *
     * @param exchange the exchange being processed
     */
    void onFailure(Exchange exchange);

    /**
     * Get an optional {@link SynchronizationRouteAware} for this synchronization
     *
     * @return An instance of {@link SynchronizationRouteAware} or null if unset for this synchronization
     */
    default @Nullable SynchronizationRouteAware getRouteSynchronization() {
        return null;
    }

}
