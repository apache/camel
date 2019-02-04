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
package org.apache.camel.processor.saga;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;

/**
 * Builder of Saga processors.
 */
public class SagaProcessorBuilder {

    private CamelContext camelContext;

    private Processor childProcessor;

    private CamelSagaService sagaService;

    private CamelSagaStep step;

    private SagaPropagation propagation;

    private SagaCompletionMode completionMode;

    public SagaProcessorBuilder() {
    }

    public SagaProcessorBuilder camelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        return this;
    }

    public SagaProcessorBuilder childProcessor(Processor childProcessor) {
        this.childProcessor = childProcessor;
        return this;
    }

    public SagaProcessorBuilder sagaService(CamelSagaService sagaService) {
        this.sagaService = sagaService;
        return this;
    }

    public SagaProcessorBuilder step(CamelSagaStep step) {
        this.step = step;
        return this;
    }

    public SagaProcessorBuilder propagation(SagaPropagation propagation) {
        this.propagation = propagation;
        return this;
    }

    public SagaProcessorBuilder completionMode(SagaCompletionMode completionMode) {
        this.completionMode = completionMode;
        return this;
    }

    public SagaProcessor build() {
        if (propagation == null) {
            throw new IllegalStateException("A propagation mode has not been set");
        }

        switch (propagation) {
        case REQUIRED:
            return new RequiredSagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        case REQUIRES_NEW:
            return new RequiresNewSagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        case SUPPORTS:
            return new SupportsSagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        case NOT_SUPPORTED:
            return new NotSupportedSagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        case NEVER:
            return new NeverSagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        case MANDATORY:
            return new MandatorySagaProcessor(camelContext, childProcessor, sagaService, completionMode, step);
        default:
            throw new IllegalStateException("Unsupported propagation mode: " + propagation);
        }
    }

}
