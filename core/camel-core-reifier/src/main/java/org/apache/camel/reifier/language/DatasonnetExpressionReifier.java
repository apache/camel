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
package org.apache.camel.reifier.language;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExpressionDefinition;

public class DatasonnetExpressionReifier extends ExpressionReifier<DatasonnetExpression> {

    public DatasonnetExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (DatasonnetExpression) definition);
    }

    @Override
    protected void configureExpression(Expression expression) {
        bindProperties(expression);
        super.configureExpression(expression);
    }

    @Override
    protected void configurePredicate(Predicate predicate) {
        bindProperties(predicate);
        super.configurePredicate(predicate);
    }

    private void bindProperties(Object target) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("inputMimeType", definition.getInputMimeType());
        properties.put("outputMimeType", definition.getOutputMimeType());
        properties.put("type", definition.getType());
        setProperties(target, properties);
    }

}
