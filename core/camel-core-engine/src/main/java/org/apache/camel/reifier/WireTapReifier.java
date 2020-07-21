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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.WireTapProcessor;

public class WireTapReifier extends ToDynamicReifier<WireTapDefinition<?>> {

    public WireTapReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // executor service is mandatory for wire tap
        boolean shutdownThreadPool = willCreateNewThreadPool(definition, true);
        ExecutorService threadPool = getConfiguredExecutorService("WireTap", definition, true);

        // must use InOnly for WireTap
        definition.setPattern(ExchangePattern.InOnly.name());

        // create the send dynamic producer to send to the wire tapped endpoint
        SendDynamicProcessor dynamicTo = (SendDynamicProcessor)super.createProcessor();

        // create error handler we need to use for processing the wire tapped
        Processor target = wrapInErrorHandler(dynamicTo, true);

        // and wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, target);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

        // is true by default
        boolean isCopy = parseBoolean(definition.getCopy(), true);

        WireTapProcessor answer = new WireTapProcessor(dynamicTo, internal,
                parse(ExchangePattern.class, definition.getPattern()),
                threadPool, shutdownThreadPool,
                parseBoolean(definition.getDynamicUri(), true));
        answer.setCopy(isCopy);
        Processor newExchangeProcessor = definition.getNewExchangeProcessor();
        if (definition.getNewExchangeProcessorRef() != null) {
            newExchangeProcessor = mandatoryLookup(parseString(definition.getNewExchangeProcessorRef()), Processor.class);
        }
        if (newExchangeProcessor != null) {
            answer.addNewExchangeProcessor(newExchangeProcessor);
        }
        if (definition.getNewExchangeExpression() != null) {
            answer.setNewExchangeExpression(createExpression(definition.getNewExchangeExpression()));
        }
        if (definition.getHeaders() != null && !definition.getHeaders().isEmpty()) {
            for (SetHeaderDefinition header : definition.getHeaders()) {
                Processor processor = createProcessor(header);
                answer.addNewExchangeProcessor(processor);
            }
        }
        Processor onPrepare = definition.getOnPrepare();
        if (definition.getOnPrepareRef() != null) {
            onPrepare = mandatoryLookup(parseString(definition.getOnPrepareRef()), Processor.class);
        }
        if (onPrepare != null) {
            answer.setOnPrepare(onPrepare);
        }

        return answer;
    }

    @Override
    protected Expression createExpression(String uri) {
        // whether to use dynamic or static uri
        if (parseBoolean(definition.getDynamicUri(), true)) {
            return super.createExpression(uri);
        } else {
            return ExpressionBuilder.constantExpression(uri);
        }
    }

}
