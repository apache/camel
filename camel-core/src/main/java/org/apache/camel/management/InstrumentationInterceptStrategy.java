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

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 * This strategy class wraps targeted processors with a
 * {@link InstrumentationProcessor}. Each InstrumentationProcessor has an
 * embedded {@link PerformanceCounter} for monitoring performance metrics.
 * <p/>
 * This class looks up a map to determine which PerformanceCounter should go into the
 * InstrumentationProcessor for any particular target processor.
 *
 * @version $Revision$
 */
public class InstrumentationInterceptStrategy implements InterceptStrategy {

    private Map<ProcessorDefinition, PerformanceCounter> registeredCounters;

    public InstrumentationInterceptStrategy(Map<ProcessorDefinition, PerformanceCounter> registeredCounters) {
        this.registeredCounters = registeredCounters;
    }

    public Processor wrapProcessorInInterceptors(ProcessorDefinition processorDefinition, Processor target) throws Exception {
        // dont double wrap it
        if (target instanceof InstrumentationProcessor) {
            return target;
        }

        // only wrap a performance counter if we have it registered in JMX by the jmx agent
        PerformanceCounter counter = registeredCounters.get(processorDefinition);
        if (counter != null) {
            InstrumentationProcessor wrapper = new InstrumentationProcessor(counter);
            wrapper.setProcessor(target);
            wrapper.setType(processorDefinition.getShortName());
            // remove to not double wrap it
            registeredCounters.remove(processorDefinition);
            return wrapper;
        }

        return target;
    }

}
