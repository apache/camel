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

import java.util.List;

import org.apache.camel.Exchange;
import org.jspecify.annotations.Nullable;

/**
 * A listener that observes where the on completions ({@link Synchronization}s) of the exchanges are: when an exchange
 * gets a unit of work and its on completions move into it, when an on completion is added to or removed from a unit of
 * work, handed over to another exchange or taken out to be run by someone else, and when it runs. A listener only
 * observes: it must not change the exchanges, the units of work or the lists it is given, and what it throws cannot
 * change the routing. It is meant for development and test tools that check the contract of the commit of a consumer,
 * described on {@link Synchronization}.
 * <p/>
 * Listeners are installed with
 * {@link org.apache.camel.ExtendedCamelContext#addOnCompletionListener(OnCompletionListener)}. Many can be installed.
 * They are called in the order they were added, and must not depend on that order. Unlike the {@link StepListener},
 * they are read at each event, so a listener added or removed while the routes run is called, or no longer called, from
 * the next event on. With no listener installed, each of these places only checks that the list of listeners is empty.
 * <p/>
 * <b>Where it is called.</b>
 * <ul>
 * <li>{@link #onUnitOfWork(Exchange, UnitOfWork, List)} when an exchange is given a unit of work (also one it shares
 * with a parent exchange), before the on completions it held until then move into it.</li>
 * <li>{@link #onCompletionAdded(UnitOfWork, Synchronization)} when an on completion is added to a unit of work of Camel
 * ({@link UnitOfWork#addSynchronization(Synchronization)}), also as part of such a move or of a handover. The adds of a
 * move come after the {@link #onUnitOfWork(Exchange, UnitOfWork, List)} of the move, and the adds of a handover before
 * the {@link #onCompletionHandover(Exchange, UnitOfWork, Exchange, List)} of the handover, on the same thread, so a
 * listener can tell them apart. Adds to an exchange that has no unit of work yet are not reported: they show up in the
 * move.</li>
 * <li>{@link #onCompletionRemoved(UnitOfWork, Synchronization)} after an on completion is removed from a unit of work
 * of Camel ({@link UnitOfWork#removeSynchronization(Synchronization)}), so it does not run.</li>
 * <li>{@link #onCompletionHandover(Exchange, UnitOfWork, Exchange, List)} after on completions are handed over from one
 * exchange to another ({@link org.apache.camel.ExchangeExtension#handoverCompletions(Exchange)}), such as to a copy
 * that is routed on its own, or back from a copy that is not routed.</li>
 * <li>{@link #onCompletionTaken(Exchange, List)} after on completions are taken out of an exchange as a list
 * ({@link org.apache.camel.ExchangeExtension#handoverCompletions()}), to be run by someone else.</li>
 * <li>{@link #onCompletionRun(Exchange, Synchronization, boolean)} before an on completion runs (when a unit of work is
 * done, or when a list of on completions that was taken out is run).</li>
 * </ul>
 * Exchange created, completed and failed are events of the {@link EventNotifier}, so they are not in this listener.
 * <p/>
 * <b>Threads.</b> The listeners are called on the thread that makes the change, by many threads at the same time: they
 * must be thread-safe and quick. The order above holds for the events of one thread; events of different threads (such
 * as the parts of a parallel split that share a unit of work) are not ordered with each other. A listener may read the
 * exchanges it is given on that thread. It should not keep the exchanges or the on completions, so these can be
 * released when the exchange is done. The lists it is given are unmodifiable, and valid during the call only.
 * <p/>
 * An exchange that is created as a copy of another ({@link Exchange#copy()}) starts with the unit of work of that
 * exchange, without {@link #onUnitOfWork(Exchange, UnitOfWork, List)}.
 * <p/>
 * <b>Failures.</b> What a listener throws cannot change the routing. It is caught and logged at WARN once per listener
 * (then at DEBUG), and the change or the run of the on completion happens as without the listener.
 * <p/>
 * All methods have an empty default.
 *
 * @since 4.24
 */
public interface OnCompletionListener {

    /**
     * An exchange is given a unit of work. The on completions it held until then move into it after this call.
     *
     * @param exchange   the exchange
     * @param unitOfWork the unit of work
     * @param moved      the on completions that move into the unit of work (empty if none)
     */
    default void onUnitOfWork(Exchange exchange, UnitOfWork unitOfWork, List<Synchronization> moved) {
    }

    /**
     * An on completion is added to a unit of work.
     *
     * @param unitOfWork   the unit of work
     * @param onCompletion the on completion
     */
    default void onCompletionAdded(UnitOfWork unitOfWork, Synchronization onCompletion) {
    }

    /**
     * An on completion was removed from a unit of work, so it does not run. Called only when it was there.
     *
     * @param unitOfWork   the unit of work
     * @param onCompletion the on completion
     */
    default void onCompletionRemoved(UnitOfWork unitOfWork, Synchronization onCompletion) {
    }

    /**
     * On completions were handed over from one exchange (and its unit of work, if it has one) to another exchange.
     * Called only when on completions moved, or when the unit of work does not tell what moved.
     *
     * @param from           the exchange that handed them over
     * @param fromUnitOfWork the unit of work of that exchange they were handed over from, or {@code null} when the
     *                       exchange held them itself (it had no unit of work)
     * @param to             the exchange they were handed over to
     * @param moved          the on completions that moved (not empty), or {@code null} when the unit of work does not
     *                       tell (a custom {@link UnitOfWork} that does not implement
     *                       {@link UnitOfWork#handoverSynchronizationWithResult(Exchange, java.util.function.Predicate)})
     */
    default void onCompletionHandover(
            Exchange from, @Nullable UnitOfWork fromUnitOfWork, Exchange to, @Nullable List<Synchronization> moved) {
    }

    /**
     * On completions were taken out of an exchange as a list, to be run by someone else.
     *
     * @param from  the exchange they were taken out of
     * @param taken the on completions (not empty)
     */
    default void onCompletionTaken(Exchange from, List<Synchronization> taken) {
    }

    /**
     * An on completion is about to run {@link Synchronization#onComplete(Exchange)} or
     * {@link Synchronization#onFailure(Exchange)}.
     *
     * @param exchange     the exchange it runs with
     * @param onCompletion the on completion
     * @param failure      {@code true} if it runs {@link Synchronization#onFailure(Exchange)}, {@code false} for
     *                     {@link Synchronization#onComplete(Exchange)}
     */
    default void onCompletionRun(Exchange exchange, Synchronization onCompletion, boolean failure) {
    }

}
