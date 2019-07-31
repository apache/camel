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

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;

/**
 * A debugger which allows tooling to attach breakpoints which is is being invoked
 * when {@link Exchange}s is being routed.
 */
public interface Debugger extends Service, CamelContextAware {

    /**
     * Add the given breakpoint
     *
     * @param breakpoint the breakpoint
     */
    void addBreakpoint(Breakpoint breakpoint);

    /**
     * Add the given breakpoint
     *
     * @param breakpoint the breakpoint
     * @param conditions a number of {@link org.apache.camel.spi.Condition}s
     */
    void addBreakpoint(Breakpoint breakpoint, Condition... conditions);

    /**
     * Add the given breakpoint which will be used in single step mode
     * <p/>
     * The debugger will single step the first message arriving.
     *
     * @param breakpoint the breakpoint
     */
    void addSingleStepBreakpoint(Breakpoint breakpoint);

    /**
     * Add the given breakpoint which will be used in single step mode
     * <p/>
     * The debugger will single step the first message arriving.
     *
     * @param breakpoint the breakpoint
     * @param conditions a number of {@link org.apache.camel.spi.Condition}s
     */
    void addSingleStepBreakpoint(Breakpoint breakpoint, Condition... conditions);

    /**
     * Removes the given breakpoint
     *
     * @param breakpoint the breakpoint
     */
    void removeBreakpoint(Breakpoint breakpoint);

    /**
     * Suspends all breakpoints.
     */
    void suspendAllBreakpoints();

    /**
     * Activate all breakpoints.
     */
    void activateAllBreakpoints();

    /**
     * Gets a list of all the breakpoints
     *
     * @return the breakpoints wrapped in an unmodifiable list, is never <tt>null</tt>.
     */
    List<Breakpoint> getBreakpoints();

    /**
     * Starts the single step debug mode for the given exchange
     *
     * @param exchangeId the exchange id
     * @param breakpoint the breakpoint
     * @return <tt>true</tt> if the debugger will single step the given exchange, <tt>false</tt> if the debugger is already
     * single stepping another, and thus cannot simultaneously single step another exchange
     */
    boolean startSingleStepExchange(String exchangeId, Breakpoint breakpoint);

    /**
     * Stops the single step debug mode for the given exchange.
     * <p/>
     * <b>Notice:</b> The default implementation of the debugger is capable of auto stopping when the exchange is complete.
     *
     * @param exchangeId the exchange id
     */
    void stopSingleStepExchange(String exchangeId);

    /**
     * Callback invoked when an {@link Exchange} is about to be processed which allows implementators
     * to notify breakpoints.
     *
     * @param exchange   the exchange
     * @param processor  the {@link Processor} about to be processed
     * @param definition the definition of the processor
     * @return <tt>true</tt> if any breakpoint was hit, <tt>false</tt> if not breakpoint was hit
     */
    boolean beforeProcess(Exchange exchange, Processor processor, NamedNode definition);

    /**
     * Callback invoked when an {@link Exchange} has been processed which allows implementators
     * to notify breakpoints.
     *
     * @param exchange   the exchange
     * @param processor  the {@link Processor} which was processed
     * @param definition the definition of the processor
     * @param timeTaken  time in millis it took to process the {@link Exchange} - time spend in breakpoint callbacks may affect this time
     * @return <tt>true</tt> if any breakpoint was hit, <tt>false</tt> if not breakpoint was hit
     */
    boolean afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken);

    /**
     * Callback invoked when an {@link Exchange} is being processed which allows implementators
     * to notify breakpoints.
     *
     * @param exchange the exchange
     * @param event    the event (instance of {@link ExchangeEvent}
     * @return <tt>true</tt> if any breakpoint was hit, <tt>false</tt> if not breakpoint was hit
     */
    boolean onEvent(Exchange exchange, ExchangeEvent event);

}
