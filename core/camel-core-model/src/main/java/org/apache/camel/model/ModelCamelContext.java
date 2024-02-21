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
package org.apache.camel.model;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;

/**
 * Model level interface for the {@link CamelContext}
 */
public interface ModelCamelContext extends CamelContext, Model {

    /**
     * Start all routes from this model.
     */
    void startRouteDefinitions() throws Exception;

    /**
     * Start the given set of routes.
     */
    void startRouteDefinitions(List<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Creates an expression from the model.
     */
    Expression createExpression(ExpressionDefinition definition);

    /**
     * Creates a predicate from the model.
     */
    Predicate createPredicate(ExpressionDefinition definition);

    /**
     * Registers the route input validator
     */
    void registerValidator(ValidatorDefinition validator);

    /**
     * Registers the route transformer
     */
    void registerTransformer(TransformerDefinition transformer);
}
