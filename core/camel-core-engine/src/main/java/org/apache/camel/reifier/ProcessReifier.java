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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.apache.camel.util.ObjectHelper;

public class ProcessReifier extends ProcessorReifier<ProcessDefinition> {

    public ProcessReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (ProcessDefinition)definition);
    }

    @Override
    public Processor createProcessor() {
        Processor answer = definition.getProcessor();
        if (answer == null) {
            ObjectHelper.notNull(definition.getRef(), "ref", definition);
            answer = mandatoryLookup(definition.getRef(), Processor.class);
        }

        // ensure its wrapped in a Service so we can manage it from eg. JMX
        // (a Processor must be a Service to be enlisted in JMX)
        if (!(answer instanceof Service)) {
            if (answer instanceof AsyncProcessor) {
                // the processor is async by nature so use the async delegate
                answer = new DelegateAsyncProcessor(answer);
            } else {
                // the processor is sync by nature so use the sync delegate
                answer = new DelegateSyncProcessor(answer);
            }
        }
        return answer;
    }
}
