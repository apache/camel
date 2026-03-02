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

import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.PropertyExpressionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.processor.saga.SagaCompletionMode;
import org.apache.camel.processor.saga.SagaProcessor;
import org.apache.camel.processor.saga.SagaProcessorBuilder;
import org.apache.camel.processor.saga.SagaPropagation;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.support.EndpointHelper;

public class SagaReifier extends ProcessorReifier<SagaDefinition> {

    public SagaReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SagaDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {

        // compensation
        String uri;
        if (definition.getCompensationEndpointProducerBuilder() != null) {
            uri = definition.getCompensationEndpointProducerBuilder().getRawUri();
        } else {
            uri = definition.getCompensation();
        }
        // route templates should pre parse uri as they have dynamic values as part of their template parameters
        RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
        if (uri != null && rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
        }
        Endpoint compensationEndpoint = null;
        if (uri != null) {
            compensationEndpoint = camelContext.getEndpoint(uri);
        }

        // completion
        if (definition.getCompletionEndpointProducerBuilder() != null) {
            uri = definition.getCompletionEndpointProducerBuilder().getRawUri();
        } else {
            uri = definition.getCompletion();
        }
        // route templates should pre parse uri as they have dynamic values as part of their template parameters
        if (uri != null && rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
        }
        Endpoint completionEndpoint = null;
        if (uri != null) {
            completionEndpoint = camelContext.getEndpoint(uri);
        }

        Map<String, Expression> optionsMap = new TreeMap<>();
        if (definition.getOptions() != null) {
            for (PropertyExpressionDefinition def : definition.getOptions()) {
                String optionName = def.getKey();
                Expression expr = createExpression(def.getExpression());
                optionsMap.put(optionName, expr);
            }
        }

        String timeout = definition.getTimeout();
        CamelSagaStep step = new CamelSagaStep(
                compensationEndpoint, completionEndpoint, optionsMap,
                parseDuration(timeout));

        SagaPropagation propagation = parse(SagaPropagation.class, definition.getPropagation());
        if (propagation == null) {
            // default propagation mode
            propagation = SagaPropagation.REQUIRED;
        }

        SagaCompletionMode completionMode = parse(SagaCompletionMode.class, definition.getCompletionMode());
        if (completionMode == null) {
            // default completion mode
            completionMode = SagaCompletionMode.defaultCompletionMode();
        }

        Processor childProcessor = this.createChildProcessor(true);
        CamelSagaService camelSagaService = resolveSagaService();
        CamelContextAware.trySetCamelContext(camelSagaService, getCamelContext());

        camelSagaService.registerStep(step);

        SagaProcessor answer = new SagaProcessorBuilder().camelContext(camelContext).childProcessor(childProcessor)
                .sagaService(camelSagaService).step(step)
                .propagation(propagation).completionMode(completionMode).build();
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }

    protected CamelSagaService resolveSagaService() {
        CamelSagaService sagaService = definition.getSagaServiceBean();
        if (sagaService != null) {
            return sagaService;
        }

        String ref = parseString(definition.getSagaService());
        if (ref != null) {
            return mandatoryLookup(ref, CamelSagaService.class);
        }

        sagaService = camelContext.hasService(CamelSagaService.class);
        if (sagaService != null) {
            return sagaService;
        }

        return mandatoryFindSingleByType(CamelSagaService.class);
    }

}
