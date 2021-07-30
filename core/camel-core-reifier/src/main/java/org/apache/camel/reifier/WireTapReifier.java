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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.StringHelper;

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

        // optimize to only use dynamic processor if really needed
        String uri;
        if (definition.getEndpointProducerBuilder() != null) {
            uri = definition.getEndpointProducerBuilder().getUri();
        } else {
            uri = StringHelper.notEmpty(definition.getUri(), "uri", this);
        }

        // route templates should pre parse uri as they have dynamic values as part of their template parameters
        RouteDefinition rd = ProcessorDefinitionHelper.getRoute(definition);
        if (rd != null && rd.isTemplate() != null && rd.isTemplate()) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
        }

        SendDynamicProcessor dynamicSendProcessor = null;
        SendProcessor sendProcessor = null;
        boolean simple = LanguageSupport.hasSimpleFunction(definition.getUri());
        boolean dynamic = parseBoolean(definition.getDynamicUri(), true);
        if (dynamic && simple) {
            // dynamic so we need the dynamic send processor
            dynamicSendProcessor = (SendDynamicProcessor) super.createProcessor();
        } else {
            // static so we can use a plain send processor
            Endpoint endpoint = CamelContextHelper.resolveEndpoint(camelContext, uri, null);
            sendProcessor = new SendProcessor(endpoint);
        }

        // create error handler we need to use for processing the wire tapped
        Processor producer = dynamicSendProcessor != null ? dynamicSendProcessor : sendProcessor;
        Processor childProcessor = wrapInErrorHandler(producer);

        // and wrap in unit of work
        AsyncProcessor target = camelContext.adapt(ExtendedCamelContext.class).getInternalProcessorFactory()
                .addUnitOfWorkProcessorAdvice(camelContext, childProcessor, route);

        // is true by default
        boolean isCopy = parseBoolean(definition.getCopy(), true);

        WireTapProcessor answer = new WireTapProcessor(
                dynamicSendProcessor, target, uri,
                parse(ExchangePattern.class, definition.getPattern()), isCopy,
                threadPool, shutdownThreadPool, dynamic);
        Processor newExchangeProcessor = definition.getNewExchangeProcessor();
        String ref = parseString(definition.getNewExchangeProcessorRef());
        if (ref != null) {
            newExchangeProcessor = mandatoryLookup(ref, Processor.class);
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
        ref = parseString(definition.getOnPrepareRef());
        if (ref != null) {
            onPrepare = mandatoryLookup(ref, Processor.class);
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
            return camelContext.resolveLanguage("constant").createExpression(uri);
        }
    }

}
