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
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;

/**
 * A condition to define when a given {@link Exchange} matches when is being routed.
 * <p/>
 * Is used by the {@link org.apache.camel.spi.Debugger} to apply {@link Condition}s to
 * {@link org.apache.camel.spi.Breakpoint}s to define rules when the breakpoints should match.
 */
public interface Condition {

    /**
     * Does the condition match
     *
     * @param  exchange   the exchange
     * @param  processor  the {@link Processor}
     * @param  definition the present location in the route where the {@link Exchange} is located at
     * @param  before     before or after processing
     * @return            <tt>true</tt> to match, <tt>false</tt> otherwise
     */
    boolean matchProcess(Exchange exchange, Processor processor, NamedNode definition, boolean before);

    /**
     * Does the condition match
     *
     * @param  exchange the exchange
     * @param  event    the event (instance of {@link ExchangeEvent}
     * @return          <tt>true</tt> to match, <tt>false</tt> otherwise
     * @see             ExchangeEvent
     */
    boolean matchEvent(Exchange exchange, ExchangeEvent event);

}
