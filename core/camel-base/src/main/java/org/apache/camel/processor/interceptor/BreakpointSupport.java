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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;

/**
 * A support class for {@link Breakpoint} implementations to use as base class.
 * <p/>
 * Will be in active state.
 */
public abstract class BreakpointSupport implements Breakpoint {

    private State state = State.Active;

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void suspend() {
        state = State.Suspended;
    }

    @Override
    public void activate() {
        state = State.Active;
    }

    @Override
    public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
        // noop
    }

    @Override
    public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
        // noop
    }

    @Override
    public void onEvent(Exchange exchange, ExchangeEvent event, NamedNode definition) {
        // noop
    }
}
