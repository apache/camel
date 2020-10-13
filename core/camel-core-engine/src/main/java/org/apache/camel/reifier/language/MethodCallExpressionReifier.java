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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.spi.Language;

public class MethodCallExpressionReifier extends ExpressionReifier<MethodCallExpression> {

    public MethodCallExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (MethodCallExpression) definition);
    }

    protected Object[] createProperties() {
        Object[] properties = new Object[5];
        properties[0] = definition.getInstance();
        properties[1] = parseString(definition.getMethod());
        properties[2] = definition.getBeanType();
        properties[3] = parseString(definition.getRef());
        properties[4] = parseString(definition.getScope());
        return properties;
    }

    @Override
    protected void configureLanguage(Language language) {
        if (definition.getBeanType() == null && definition.getBeanTypeName() != null) {
            try {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(definition.getBeanTypeName());
                definition.setBeanType(clazz);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    @Override
    protected Expression createExpression(Language language, String exp) {
        return language.createExpression(exp, createProperties());
    }

    @Override
    protected Predicate createPredicate(Language language, String exp) {
        return language.createPredicate(exp, createProperties());
    }
}
