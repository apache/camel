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

import java.util.concurrent.ExecutorService;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnCompletionMode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.OnCompletionProcessor;

public class OnCompletionReifier extends ProcessorReifier<OnCompletionDefinition> {

    public OnCompletionReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (OnCompletionDefinition)definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        boolean isOnCompleteOnly = parseBoolean(definition.getOnCompleteOnly(), false);
        boolean isOnFailureOnly = parseBoolean(definition.getOnFailureOnly(), false);
        boolean isParallelProcessing = parseBoolean(definition.getParallelProcessing(), false);
        boolean original = parseBoolean(definition.getUseOriginalMessage(), false);

        if (isOnCompleteOnly && isOnFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
        if (original) {
            // ensure allow original is turned on
            route.setAllowUseOriginalMessage(true);
        }

        Processor childProcessor = this.createChildProcessor(true);

        // wrap the on completion route in a unit of work processor
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, childProcessor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

        route.setOnCompletion(getId(definition), internal);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = createPredicate(definition.getOnWhen().getExpression());
        }

        boolean shutdownThreadPool = willCreateNewThreadPool(definition, isParallelProcessing);
        ExecutorService threadPool = getConfiguredExecutorService("OnCompletion", definition, isParallelProcessing);

        // should be after consumer by default
        boolean afterConsumer = definition.getMode() == null || parse(OnCompletionMode.class, definition.getMode()) == OnCompletionMode.AfterConsumer;

        OnCompletionProcessor answer = new OnCompletionProcessor(camelContext, internal, threadPool, shutdownThreadPool, isOnCompleteOnly, isOnFailureOnly, when,
                                                                 original, afterConsumer);
        return answer;
    }

}
