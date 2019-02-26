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

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SagaActionUriDefinition;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.SagaOptionDefinition;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.processor.saga.SagaProcessorBuilder;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

class SagaReifier extends ProcessorReifier<SagaDefinition> {

    SagaReifier(ProcessorDefinition<?> definition) {
        super((SagaDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Optional<Endpoint> compensationEndpoint = Optional.ofNullable(definition.getCompensation())
                .map(SagaActionUriDefinition::getUri)
                .map(routeContext::resolveEndpoint);

        Optional<Endpoint> completionEndpoint = Optional.ofNullable(definition.getCompletion())
                .map(SagaActionUriDefinition::getUri)
                .map(routeContext::resolveEndpoint);

        Map<String, Expression> optionsMap = new TreeMap<>();
        if (definition.getOptions() != null) {
            for (SagaOptionDefinition optionDef : definition.getOptions()) {
                String optionName = optionDef.getOptionName();
                Expression expr = optionDef.getExpression();
                optionsMap.put(optionName, expr);
            }
        }

        CamelSagaStep step = new CamelSagaStep(compensationEndpoint, completionEndpoint, optionsMap, Optional.ofNullable(definition.getTimeoutInMilliseconds()));

        SagaPropagation propagation = definition.getPropagation();
        if (propagation == null) {
            // default propagation mode
            propagation = SagaPropagation.REQUIRED;
        }

        SagaCompletionMode completionMode = definition.getCompletionMode();
        if (completionMode == null) {
            // default completion mode
            completionMode = SagaCompletionMode.defaultCompletionMode();
        }

        Processor childProcessor = this.createChildProcessor(routeContext, true);
        CamelSagaService camelSagaService = findSagaService(routeContext.getCamelContext());

        camelSagaService.registerStep(step);

        return new SagaProcessorBuilder()
                .camelContext(routeContext.getCamelContext())
                .childProcessor(childProcessor)
                .sagaService(camelSagaService)
                .step(step)
                .propagation(propagation)
                .completionMode(completionMode)
                .build();
    }

    protected CamelSagaService findSagaService(CamelContext context) {
        CamelSagaService sagaService = definition.getSagaService();
        if (sagaService != null) {
            return sagaService;
        }

        sagaService = context.hasService(CamelSagaService.class);
        if (sagaService != null) {
            return sagaService;
        }

        sagaService = CamelContextHelper.findByType(context, CamelSagaService.class);
        if (sagaService != null) {
            return sagaService;
        }

        throw new RuntimeCamelException("Cannot find a CamelSagaService");
    }

}
