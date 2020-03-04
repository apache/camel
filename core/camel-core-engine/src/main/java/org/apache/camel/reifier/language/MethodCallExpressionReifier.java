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
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.spi.Language;

public class MethodCallExpressionReifier extends ExpressionReifier<MethodCallExpression> {

    public MethodCallExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (MethodCallExpression) definition);
    }

    protected void configureLanguage(Language language) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bean", definition.getInstance());
        properties.put("beanType", or(definition.getBeanType(), definition.getBeanTypeName()));
        properties.put("ref", definition.getRef());
        properties.put("method", definition.getMethod());
        setProperties(language, properties);
    }

    @Override
    public Predicate createPredicate() {
        return (Predicate) createExpression();
    }

}
