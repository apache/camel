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
package org.apache.camel.management;

import java.util.Map;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.util.KeyValueHolder;

/**
 * This strategy class wraps targeted processors with a
 * {@link InstrumentationProcessor}. Each InstrumentationProcessor has an
 * embedded {@link ManagedPerformanceCounter} for monitoring performance metrics.
 * <p/>
 * This class looks up a map to determine which PerformanceCounter should go into the
 * InstrumentationProcessor for any particular target processor.
 */
public class InstrumentationInterceptStrategy implements ManagementInterceptStrategy {

    private Map<NamedNode, PerformanceCounter> registeredCounters;
    private final Map<Processor, KeyValueHolder<NamedNode, InstrumentationProcessor>> wrappedProcessors;

    public InstrumentationInterceptStrategy(Map<NamedNode, PerformanceCounter> registeredCounters,
            Map<Processor, KeyValueHolder<NamedNode, InstrumentationProcessor>> wrappedProcessors) {
        this.registeredCounters = registeredCounters;
        this.wrappedProcessors = wrappedProcessors;
    }

    @Override
    public InstrumentationProcessor<?> createProcessor(String type) {
        return new DefaultInstrumentationProcessor(type);
    }

    @Override
    public InstrumentationProcessor<?> createProcessor(NamedNode definition, Processor target) {
        InstrumentationProcessor instrumentationProcessor = new DefaultInstrumentationProcessor(definition.getShortName(), target);
        PerformanceCounter counter = registeredCounters.get(definition);
        if (counter != null) {
            // add it to the mapping of wrappers so we can later change it to a
            // decorated counter when we register the processor
            KeyValueHolder<NamedNode, InstrumentationProcessor> holder = new KeyValueHolder<>(definition, instrumentationProcessor);
            wrappedProcessors.put(target, holder);
        }
        return instrumentationProcessor;
    }

}
