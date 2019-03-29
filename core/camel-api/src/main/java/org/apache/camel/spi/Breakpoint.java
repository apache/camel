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

import java.util.EventObject;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;

/**
 * {@link org.apache.camel.spi.Breakpoint} are used by the {@link org.apache.camel.spi.Debugger} API.
 * <p/>
 * This allows you to register {@link org.apache.camel.spi.Breakpoint}s to the {@link org.apache.camel.spi.Debugger}
 * and have those breakpoints activated when their {@link org.apache.camel.spi.Condition}s match.
 * <p/>
 * If any exceptions is thrown from the callback methods then the {@link org.apache.camel.spi.Debugger}
 * will catch and log those at <tt>WARN</tt> level and continue. This ensures Camel can continue to route
 * the message without having breakpoints causing issues.
 * @see org.apache.camel.spi.Debugger
 * @see org.apache.camel.spi.Condition
 */
public interface Breakpoint {

    /**
     * State of the breakpoint as either active or suspended.
     */
    enum State {
        Active, Suspended
    }

    /**
     * Gets the state of this break
     *
     * @return the state
     */
    State getState();

    /**
     * Suspend this breakpoint
     */
    void suspend();

    /**
     * Activates this breakpoint
     */
    void activate();

    /**
     * Callback invoked when the breakpoint was hit and the {@link Exchange} is about to be processed (before).
     *  @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} about to be processed
     * @param definition the {@link NamedNode} definition of the processor
     */
    void beforeProcess(Exchange exchange, Processor processor, NamedNode definition);

    /**
     * Callback invoked when the breakpoint was hit and the {@link Exchange} has been processed (after).
     *  @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} which was processed
     * @param definition the {@link NamedNode} definition of the processor
     * @param timeTaken  time in millis it took to process the {@link Exchange} - time spend in breakpoint callbacks may affect this time
     */
    void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken);

    /**
     * Callback invoked when the breakpoint was hit and any of the {@link Exchange} {@link EventObject event}s occurred.
     *
     * @param exchange   the {@link Exchange}
     * @param event      the event (instance of {@link ExchangeEvent}
     * @param definition the {@link NamedNode} definition of the last processor executed,
     *                   may be <tt>null</tt> if not possible to resolve from tracing
     * @see ExchangeEvent
     */
    void onEvent(Exchange exchange, ExchangeEvent event, NamedNode definition);

}
