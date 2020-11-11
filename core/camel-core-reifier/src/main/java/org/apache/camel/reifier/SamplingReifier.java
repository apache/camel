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
package org.apache.camel.reifier;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.processor.SamplingThrottler;

public class SamplingReifier extends ProcessorReifier<SamplingDefinition> {

    public SamplingReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SamplingDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        if (definition.getMessageFrequency() != null) {
            return new SamplingThrottler(parseLong(definition.getMessageFrequency()));
        } else {
            // should default be 1 sample period
            long time = definition.getSamplePeriod() != null ? parseDuration(definition.getSamplePeriod()) : 1L;
            // should default be in seconds
            TimeUnit tu = definition.getUnits() != null ? parse(TimeUnit.class, definition.getUnits()) : TimeUnit.SECONDS;
            return new SamplingThrottler(time, tu);
        }
    }
}
