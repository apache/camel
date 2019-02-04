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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

class WireTapReifier extends ToDynamicReifier<WireTapDefinition<?>> {

    WireTapReifier(ProcessorDefinition<?> definition) {
        super(definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // executor service is mandatory for wire tap
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, true);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "WireTap", definition, true);

        // must use InOnly for WireTap
        definition.setPattern(ExchangePattern.InOnly);

        // create the send dynamic producer to send to the wire tapped endpoint
        SendDynamicProcessor dynamicTo = (SendDynamicProcessor) super.createProcessor(routeContext);

        // create error handler we need to use for processing the wire tapped
        Processor target = wrapInErrorHandler(routeContext, dynamicTo);

        // and wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(target);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        // is true by default
        boolean isCopy = definition.getCopy() == null || definition.getCopy();

        WireTapProcessor answer = new WireTapProcessor(dynamicTo, internal, definition.getPattern(), threadPool, shutdownThreadPool, definition.isDynamic());
        answer.setCopy(isCopy);
        if (definition.getNewExchangeProcessorRef() != null) {
            definition.setNewExchangeProcessor(routeContext.mandatoryLookup(definition.getNewExchangeProcessorRef(), Processor.class));
        }
        if (definition.getNewExchangeProcessor() != null) {
            answer.addNewExchangeProcessor(definition.getNewExchangeProcessor());
        }
        if (definition.getNewExchangeExpression() != null) {
            answer.setNewExchangeExpression(definition.getNewExchangeExpression().createExpression(routeContext));
        }
        if (definition.getHeaders() != null && !definition.getHeaders().isEmpty()) {
            for (SetHeaderDefinition header : definition.getHeaders()) {
                Processor processor = createProcessor(routeContext, header);
                answer.addNewExchangeProcessor(processor);
            }
        }
        if (definition.getOnPrepareRef() != null) {
            definition.setOnPrepare(CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), definition.getOnPrepareRef(), Processor.class));
        }
        if (definition.getOnPrepare() != null) {
            answer.setOnPrepare(definition.getOnPrepare());
        }

        return answer;
    }

    @Override
    protected Expression createExpression(RouteContext routeContext) {
        // whether to use dynamic or static uri
        if (definition.isDynamic()) {
            return super.createExpression(routeContext);
        } else {
            return ExpressionBuilder.constantExpression(definition.getUri());
        }
    }

}
