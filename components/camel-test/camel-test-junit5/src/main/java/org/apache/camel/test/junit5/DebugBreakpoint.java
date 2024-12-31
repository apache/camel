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
package org.apache.camel.test.junit5;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.support.BreakpointSupport;

/**
 * To easily debug by overriding the <tt>debugBefore</tt> and <tt>debugAfter</tt> methods.
 */
public abstract class DebugBreakpoint extends BreakpointSupport {

    @Override
    public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
        debugBefore(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                definition.getLabel());
    }

    @Override
    public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
        debugAfter(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                definition.getLabel(), timeTaken);
    }

    /**
     * Single step debugs and Camel invokes this method before entering the given processor
     *
     * @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} which was processed
     * @param definition the {@link ProcessorDefinition} definition of the processor
     * @param id         the definition ID
     * @param label      the definition label
     */
    protected abstract void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label);

    /**
     * Single step debugs and Camel invokes this method after processing the given processor
     *
     * @param exchange   the {@link Exchange}
     * @param processor  the {@link Processor} which was processed
     * @param definition the {@link ProcessorDefinition} definition of the processor
     * @param id         the definition ID
     * @param label      the definition label
     */
    protected abstract void debugAfter(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label,
            long timeTaken);
}
