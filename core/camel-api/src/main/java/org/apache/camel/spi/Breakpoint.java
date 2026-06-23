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
import org.jspecify.annotations.Nullable;

/**
 * Callback interface for a debugger breakpoint, registered with the {@link Debugger} and invoked as
 * {@link org.apache.camel.Exchange}s pass through route nodes.
 * <p/>
 * Breakpoints are registered via {@link Debugger#addBreakpoint(Breakpoint)} (unconditional) or
 * {@link Debugger#addBreakpoint(Breakpoint, Condition...)} (conditional). When all provided {@link Condition}s are
 * satisfied, the {@code Debugger} calls the appropriate {@code before*} / {@code after*} callback on this interface.
 * Multiple {@code before*} methods exist to differentiate the type of routing event: entering a processor node,
 * handling an exchange event, and so on.
 * <p/>
 * Exceptions thrown from any callback method are caught by the {@code Debugger} and logged at {@code WARN} level. This
 * ensures that a buggy breakpoint does not abort routing of the exchange.
 * <p/>
 * A breakpoint can be in {@link State#Active} or {@link State#Suspended} state. The {@link BacklogDebugger} uses the
 * suspended state to pause exchange processing and wait for a resume signal from a debugging client.
 *
 * @see Debugger
 * @see Condition
 * @see BacklogDebugger
 */
public interface Breakpoint {

    /**
     * State of the breakpoint as either active or suspended.
     */
    enum State {
        Active,
        Suspended
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
     *
     * @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} about to be processed
     * @param definition the {@link NamedNode} definition of the processor
     */
    void beforeProcess(Exchange exchange, Processor processor, NamedNode definition);

    /**
     * Callback invoked when the breakpoint was hit and the {@link Exchange} has been processed (after).
     *
     * @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} which was processed
     * @param definition the {@link NamedNode} definition of the processor
     * @param timeTaken  time in millis it took to process the {@link Exchange} - time spend in breakpoint callbacks may
     *                   affect this time
     */
    void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken);

    /**
     * Callback invoked when the breakpoint was hit and any of the {@link Exchange} {@link EventObject event}s occurred.
     *
     * @param exchange   the {@link Exchange}
     * @param event      the event (instance of {@link ExchangeEvent}
     * @param definition the {@link NamedNode} definition of the last processor executed, may be <tt>null</tt> if not
     *                   possible to resolve from tracing
     * @see              ExchangeEvent
     */
    void onEvent(Exchange exchange, ExchangeEvent event, @Nullable NamedNode definition);

}
