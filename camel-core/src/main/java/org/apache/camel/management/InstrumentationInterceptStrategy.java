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
package org.apache.camel.management;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.api.management.PerformanceCounter;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.util.KeyValueHolder;

/**
 * This strategy class wraps targeted processors with a
 * {@link InstrumentationProcessor}. Each InstrumentationProcessor has an
 * embedded {@link ManagedPerformanceCounter} for monitoring performance metrics.
 * <p/>
 * This class looks up a map to determine which PerformanceCounter should go into the
 * InstrumentationProcessor for any particular target processor.
 *
 * @version 
 */
public class InstrumentationInterceptStrategy implements InterceptStrategy {

    private Map<ProcessorDefinition<?>, PerformanceCounter> registeredCounters;
    private final Map<Processor, KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor>> wrappedProcessors;

    public InstrumentationInterceptStrategy(Map<ProcessorDefinition<?>, PerformanceCounter> registeredCounters,
            Map<Processor, KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor>> wrappedProcessors) {
        this.registeredCounters = registeredCounters;
        this.wrappedProcessors = wrappedProcessors;
    }

    public PerformanceCounter prepareProcessor(ProcessorDefinition<?> definition, Processor target, InstrumentationProcessor advice) {
        PerformanceCounter counter = registeredCounters.get(definition);
        if (counter != null) {
            // add it to the mapping of wrappers so we can later change it to a
            // decorated counter when we register the processor
            KeyValueHolder<ProcessorDefinition<?>, InstrumentationProcessor> holder = new KeyValueHolder<>(definition, advice);
            wrappedProcessors.put(target, holder);
        }
        return counter;
    }

    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition,
                                                 Processor target, Processor nextTarget) throws Exception {
        // no longer in use as we have optimised to avoid wrapping unless needed
        return target;
    }

}
