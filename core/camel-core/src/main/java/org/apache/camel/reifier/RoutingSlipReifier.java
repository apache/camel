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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutingSlipDefinition;

import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.spi.RouteContext;

import static org.apache.camel.model.RoutingSlipDefinition.DEFAULT_DELIMITER;

class RoutingSlipReifier extends ExpressionReifier<RoutingSlipDefinition<?>> {

    RoutingSlipReifier(ProcessorDefinition<?> definition) {
        super((RoutingSlipDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expression = definition.getExpression().createExpression(routeContext);
        String delimiter = definition.getUriDelimiter() != null ? definition.getUriDelimiter() : DEFAULT_DELIMITER;

        RoutingSlip routingSlip = new RoutingSlip(routeContext.getCamelContext(), expression, delimiter);
        if (definition.getIgnoreInvalidEndpoints() != null) {
            routingSlip.setIgnoreInvalidEndpoints(definition.getIgnoreInvalidEndpoints());
        }
        if (definition.getCacheSize() != null) {
            routingSlip.setCacheSize(definition.getCacheSize());
        }

        // and wrap this in an error handler
        ErrorHandlerFactory builder = ((RouteDefinition) routeContext.getRoute()).getErrorHandlerBuilder();
        // create error handler (create error handler directly to keep it light weight,
        // instead of using ProcessorDefinition.wrapInErrorHandler)
        AsyncProcessor errorHandler = (AsyncProcessor) builder.createErrorHandler(routeContext, routingSlip.newRoutingSlipProcessorForErrorHandler());
        routingSlip.setErrorHandler(errorHandler);

        return routingSlip;
    }

}
