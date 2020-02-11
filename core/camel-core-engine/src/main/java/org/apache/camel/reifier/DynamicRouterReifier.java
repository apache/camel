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
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.RouteContext;

public class DynamicRouterReifier extends ExpressionReifier<DynamicRouterDefinition<?>> {

    public DynamicRouterReifier(RouteContext routeContext, ProcessorDefinition<?> definition) {
        super(routeContext, DynamicRouterDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        Expression expression = createExpression(definition.getExpression());
        String delimiter = definition.getUriDelimiter() != null ? definition.getUriDelimiter() : DynamicRouterDefinition.DEFAULT_DELIMITER;

        DynamicRouter dynamicRouter = new DynamicRouter(camelContext, expression, delimiter);
        if (definition.getIgnoreInvalidEndpoints() != null) {
            dynamicRouter.setIgnoreInvalidEndpoints(parseBoolean(definition.getIgnoreInvalidEndpoints(), false));
        }
        if (definition.getCacheSize() != null) {
            dynamicRouter.setCacheSize(parseInt(definition.getCacheSize()));
        }

        // and wrap this in an error handler
        ErrorHandlerFactory builder = routeContext.getErrorHandlerFactory();
        // create error handler (create error handler directly to keep it light
        // weight,
        // instead of using ProcessorReifier.wrapInErrorHandler)
        AsyncProcessor errorHandler = (AsyncProcessor)ErrorHandlerReifier.reifier(routeContext, builder).createErrorHandler(dynamicRouter.newRoutingSlipProcessorForErrorHandler());
        dynamicRouter.setErrorHandler(errorHandler);

        return dynamicRouter;
    }

}
