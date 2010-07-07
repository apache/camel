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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

/**
 * {@link org.apache.camel.spi.Breakpoint} are used by the {@link org.apache.camel.spi.Debugger} API.
 * <p/>
 * This allows you to register {@link org.apache.camel.spi.Breakpoint}s to the {@link org.apache.camel.spi.Debugger}
 * and have those breakpoints activated when their {@link org.apache.camel.spi.Condition}s match.
 * <p/>
 * If any exceptions is thrown from the {@link #onExchange(org.apache.camel.Exchange, org.apache.camel.Processor, org.apache.camel.model.ProcessorDefinition)}
 * method then the {@link org.apache.camel.spi.Debugger} will catch and log those at <tt>WARN</tt> level and continue.
 *
 * @see org.apache.camel.spi.Debugger
 * @see org.apache.camel.spi.Condition
 * @version $Revision$
 */
public interface Breakpoint {

    // TODO: Hook into the EventNotifier so we can have breakpoints trigger on those conditions as well
    // exceptions, create, done, etc. and a FollowMe condition to follow a single exchange
    // while others are being routed so you can follow one only, eg need an API on Debugger for that

    enum State { Active, Suspended }

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
     * Callback invoked when the breakpoint was hit.
     *
     * @param exchange    the {@link Exchange}
     * @param processor   the {@link Processor} which is the next target
     * @param definition  the {@link org.apache.camel.model.ProcessorDefinition} definition of the processor
     */
    void onExchange(Exchange exchange, Processor processor, ProcessorDefinition definition);

}
