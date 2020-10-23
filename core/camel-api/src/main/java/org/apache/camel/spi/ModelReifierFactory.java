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

    Route createRoute(CamelContext camelContext, Object routeDefinition);

    DataFormat createDataFormat(CamelContext camelContext, Object dataFormatDefinition);

    Processor createErrorHandler(Route route, Processor processor) throws Exception;

    Processor createErrorHandler(Route route, ErrorHandlerFactory errorHandlerFactory, Processor processor) throws Exception;

    ErrorHandlerFactory createDefaultErrorHandler();

    Expression createExpression(CamelContext camelContext, Object expressionDefinition);

    Predicate createPredicate(CamelContext camelContext, Object expressionDefinition);

    Transformer createTransformer(CamelContext camelContext, Object transformerDefinition);

    Validator createValidator(CamelContext camelContext, Object transformerDefinition);

}
