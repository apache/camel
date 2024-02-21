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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;

/**
 * Factory that uses reifiers to build an entity from a given model.
 */
public interface ModelReifierFactory {

    /**
     * Service factory key for custom factories.
     */
    String FACTORY = "model-reifier-factory";

    /**
     * Creates the route from the model.
     */
    Route createRoute(CamelContext camelContext, Object routeDefinition);

    /**
     * Creates the data format from the model.
     */
    DataFormat createDataFormat(CamelContext camelContext, Object dataFormatDefinition);

    /**
     * Creates the error handler for the route processor.
     */
    Processor createErrorHandler(Route route, Processor processor) throws Exception;

    /**
     * Creates the error handler using the factory for the route processor.
     */
    Processor createErrorHandler(Route route, ErrorHandlerFactory errorHandlerFactory, Processor processor) throws Exception;

    /**
     * Creates the default error handler.
     */
    ErrorHandlerFactory createDefaultErrorHandler();

    /**
     * Creates the expression from the model.
     */
    Expression createExpression(CamelContext camelContext, Object expressionDefinition);

    /**
     * Creates the predicate from the model.
     */
    Predicate createPredicate(CamelContext camelContext, Object expressionDefinition);

    /**
     * Creates the transformer from the model.
     */
    Transformer createTransformer(CamelContext camelContext, Object transformerDefinition);

    /**
     * Creates the validator from the model.
     */
    Validator createValidator(CamelContext camelContext, Object transformerDefinition);

}
