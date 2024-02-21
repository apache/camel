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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.reifier.language.ExpressionReifier;
import org.apache.camel.reifier.transformer.TransformerReifier;
import org.apache.camel.reifier.validator.ValidatorReifier;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;

/**
 * Default {@link ModelReifierFactory}
 */
public class DefaultModelReifierFactory implements ModelReifierFactory {

    @Override
    public Route createRoute(CamelContext camelContext, Object routeDefinition) {
        return new RouteReifier(camelContext, (ProcessorDefinition<?>) routeDefinition).createRoute();
    }

    @Override
    public DataFormat createDataFormat(CamelContext camelContext, Object dataFormatDefinition) {
        return DataFormatReifier.reifier(camelContext, (DataFormatDefinition) dataFormatDefinition).createDataFormat();
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return createErrorHandler(route, route.getErrorHandlerFactory(), processor);
    }

    @Override
    public Processor createErrorHandler(Route route, ErrorHandlerFactory errorHandlerFactory, Processor processor)
            throws Exception {
        return ErrorHandlerReifier.reifier(route, errorHandlerFactory).createErrorHandler(processor);
    }

    @Override
    public ErrorHandlerFactory createDefaultErrorHandler() {
        return new DefaultErrorHandlerDefinition();
    }

    @Override
    public Expression createExpression(CamelContext camelContext, Object expressionDefinition) {
        return ExpressionReifier.reifier(camelContext, (ExpressionDefinition) expressionDefinition).createExpression();
    }

    @Override
    public Predicate createPredicate(CamelContext camelContext, Object expressionDefinition) {
        return ExpressionReifier.reifier(camelContext, (ExpressionDefinition) expressionDefinition).createPredicate();
    }

    @Override
    public Transformer createTransformer(CamelContext camelContext, Object transformerDefinition) {
        return TransformerReifier.reifier(camelContext, (TransformerDefinition) transformerDefinition).createTransformer();
    }

    @Override
    public Validator createValidator(CamelContext camelContext, Object transformerDefinition) {
        return ValidatorReifier.reifier(camelContext, (ValidatorDefinition) transformerDefinition).createValidator();
    }
}
