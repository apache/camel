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
import org.apache.camel.model.language.TypedExpressionDefinition;
import org.apache.camel.spi.Language;

/**
 * {@code TypedExpressionReifier} is a specific reifier for expressions for which a result type can be provided.
 *
 * @param <T> the type of expression
 */
class TypedExpressionReifier<T extends TypedExpressionDefinition> extends ExpressionReifier<T> {

    @SuppressWarnings("unchecked")
    TypedExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (T) definition);
    }

    @Override
    protected Expression createExpression(Language language, String exp) {
        return language.createExpression(exp, createProperties());
    }

    @Override
    protected Predicate createPredicate(Language language, String exp) {
        return language.createPredicate(exp, createProperties());
    }

    protected Object[] createProperties() {
        Object[] properties = new Object[1];
        properties[0] = asResultType();
        return properties;
    }

    protected Class<?> asResultType() {
        if (definition.getResultType() == null && definition.getResultTypeName() != null) {
            try {
                return camelContext.getClassResolver().resolveMandatoryClass(parseString(definition.getResultTypeName()));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        return definition.getResultType();
    }

    @Override
    protected void configureLanguage(Language language) {
        asResultType();
    }
}
