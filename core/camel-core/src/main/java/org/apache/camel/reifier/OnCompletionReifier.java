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
package org.apache.camel.reifier;

import java.util.concurrent.ExecutorService;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnCompletionMode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.OnCompletionProcessor;
import org.apache.camel.spi.RouteContext;

class OnCompletionReifier extends ProcessorReifier<OnCompletionDefinition> {

    OnCompletionReifier(ProcessorDefinition<?> definition) {
        super((OnCompletionDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // assign whether this was a route scoped onCompletion or not
        // we need to know this later when setting the parent, as only route scoped should have parent
        // Note: this logic can possible be removed when the Camel routing engine decides at runtime
        // to apply onCompletion in a more dynamic fashion than current code base
        // and therefore is in a better position to decide among context/route scoped OnCompletion at runtime
        Boolean routeScoped = definition.getRouteScoped();
        if (routeScoped == null) {
            routeScoped = definition.getParent() != null;
        }

        boolean isOnCompleteOnly = definition.getOnCompleteOnly() != null && definition.getOnCompleteOnly();
        boolean isOnFailureOnly = definition.getOnFailureOnly() != null && definition.getOnFailureOnly();
        boolean isParallelProcessing = definition.getParallelProcessing() != null && definition.getParallelProcessing();
        boolean original = definition.getUseOriginalMessagePolicy() != null && definition.getUseOriginalMessagePolicy();

        if (isOnCompleteOnly && isOnFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
        if (original) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        String routeId = ((RouteDefinition) routeContext.getRoute()).idOrCreate(routeContext.getCamelContext().getNodeIdFactory());

        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap the on completion route in a unit of work processor
        CamelInternalProcessor internal = new CamelInternalProcessor(childProcessor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        definition.setOnCompletion(routeId, internal);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = definition.getOnWhen().getExpression().createPredicate(routeContext);
        }

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, isParallelProcessing);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "OnCompletion", definition, isParallelProcessing);

        // should be after consumer by default
        boolean afterConsumer = definition.getMode() == null || definition.getMode() == OnCompletionMode.AfterConsumer;

        OnCompletionProcessor answer = new OnCompletionProcessor(routeContext.getCamelContext(), internal,
                threadPool, shutdownThreadPool, isOnCompleteOnly, isOnFailureOnly, when, original, afterConsumer);
        return answer;
    }


}
