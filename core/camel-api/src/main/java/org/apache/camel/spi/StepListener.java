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
 * A listener that observes each call of the processor of a step of a route: the call, its return or what it throws, and
 * each call of its callback. A step is any node of a route with its node id ({@code to}, {@code process},
 * {@code split}, ...), the same steps the {@link StepHook} is asked for. A listener only observes: it must not change
 * the exchange, and what it throws cannot change the routing. It is meant for development and test tools that check the
 * contracts of {@link org.apache.camel.AsyncProcessor} and {@link org.apache.camel.AsyncCallback}.
 * <p/>
 * Listeners are installed with {@link org.apache.camel.ExtendedCamelContext#addStepListener(StepListener)}. Unlike the
 * {@link StepHook}, many can be installed. They are called in the order they were added, and must not depend on that
 * order.
 * <p/>
 * <b>Where it is called.</b> Whether a step gets the listeners is decided when its route is created. When at least one
 * listener is installed then, the channel of each step (the default channel of Camel, not a channel of a custom
 * {@link InternalProcessorFactory}) adds a small wrapper around the processor of the step and the
 * {@link InterceptStrategy} wrappers of the step, below the error handler of the step. So the listeners see each call
 * of that chain: each attempt, also each redelivery of the error handler, and each call by the channel of a step
 * without such an error handler. The {@link StepHook} is asked before the wrapper is called, so a step that the hook
 * fails is not a call the listeners see. A route that is created while no listener is installed has no wrapper and is
 * not seen until it is created again; add the listeners before the routes are created. With no listener installed when
 * a route is created, the route is built as without this SPI, and a step costs nothing more. A step that has the
 * wrapper reads the listeners installed at the start of each call, so a listener added later is called by the routes
 * that have the wrapper.
 * <p/>
 * With no {@link InterceptStrategy} on the step, the listeners see the call of the processor of the step itself. A
 * strategy can hide part of that call: {@code intercept()} calls the processor later, after the intercept route, so the
 * listeners do not see its own return value or what it throws; a strategy that returns a plain
 * {@link org.apache.camel.Processor} makes the call wait until the processor is done, so a second call of the callback
 * of the processor is not seen; and a strategy that catches what the processor throws hides the throw.
 * <p/>
 * <b>The state of a call.</b> {@link #onStepCall(Exchange, String, String)} returns a state for the call, and the other
 * methods get it back. The state is held by the callback of the call, so it lives as long as anything can still call
 * that callback; it should therefore hold ids, names, threads or stacks, never the exchange.
 * <p/>
 * <b>Threads.</b> The listeners are called on the thread of the event, by many threads at the same time: they must be
 * thread-safe and quick. Only {@link #onStepCall(Exchange, String, String)} may read the exchange. The other methods
 * can run on a thread that does not own the exchange, so they get only the state.
 * {@link #onStepCallback(Object, boolean)} can come before {@link #onStepReturn(Object, boolean)}, on the same thread
 * (the callback is called before {@code process} returns) or on another one.
 * <p/>
 * <b>Failures.</b> What a listener throws cannot change the routing. It is caught and logged at WARN once per listener
 * (then at DEBUG). The processor is called whatever {@link #onStepCall(Exchange, String, String)} did, the callback of
 * the call is called after the listeners, and what the processor throws is thrown on unchanged. A listener whose
 * {@link #onStepCall(Exchange, String, String)} threw gets no more events for that call.
 * <p/>
 * A call keeps the listeners that were installed when it started, so a listener that is removed meanwhile still gets
 * the callback of a call it saw start.
 * <p/>
 * All methods have an empty default. Their names differ from {@link StepHook#beforeStep(Exchange, String)}, so one
 * class can implement both.
 *
 * @since 4.24
 */
public interface StepListener {

    /**
     * The processor of a step is about to be called (each attempt, also each redelivery).
     *
     * @param  exchange the exchange
     * @param  routeId  the id of the route of the step
     * @param  nodeId   the id of the step: the id of its node in the route, such as {@code to1}, or the id set with
     *                  {@code id("myStep")}
     * @return          the state of this call, passed to the other methods of this listener for this call; can be
     *                  {@code null}
     */
    default @Nullable Object onStepCall(Exchange exchange, String routeId, String nodeId) {
        return null;
    }

    /**
     * {@code process(exchange, callback)} of the call returned.
     *
     * @param state    the state returned by {@link #onStepCall(Exchange, String, String)} for this call
     * @param doneSync the value {@code process} returned
     */
    default void onStepReturn(@Nullable Object state, boolean doneSync) {
    }

    /**
     * {@code process(exchange, callback)} of the call threw. The exception is thrown on unchanged after the listeners.
     *
     * @param state the state returned by {@link #onStepCall(Exchange, String, String)} for this call
     * @param cause what {@code process} threw
     */
    default void onStepThrow(@Nullable Object state, Throwable cause) {
    }

    /**
     * The callback of the call is called, before it continues the exchange. It is called each time the callback is
     * called, so a second call of the callback is seen too.
     *
     * @param state    the state returned by {@link #onStepCall(Exchange, String, String)} for this call
     * @param doneSync the value the callback is called with
     */
    default void onStepCallback(@Nullable Object state, boolean doneSync) {
    }

}
