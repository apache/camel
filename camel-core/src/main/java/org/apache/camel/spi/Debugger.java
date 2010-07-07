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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;

/**
 * A debugger which allows tooling to attach breakpoints which is is being invoked
 * when {@link Exchange}s is being routed.
 *
 * @version $Revision$
 */
public interface Debugger extends Service {

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
     * Callback invoked when an {@link Exchange} is being processed which allows implementators
     * to notify breakpoints.
     *
     * @param exchange     the exchange
     * @param processor    the target processor (to be processed next)
     * @param definition   the definition of the processor
     * @return <tt>true</tt> if any breakpoint was hit, <tt>false</tt> if not breakpoint was hit
     */
    boolean onExchange(Exchange exchange, Processor processor, ProcessorDefinition definition);

}
