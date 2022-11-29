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
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NamespaceAware;

public class XQueryExpressionReifier extends ExpressionReifier<XQueryExpression> {

    public XQueryExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (XQueryExpression) definition);
    }

    @Override
    protected Expression createExpression(Language language, String exp) {
        return language.createExpression(exp, createProperties());
    }

    @Override
    protected Predicate createPredicate(Language language, String exp) {
        return language.createPredicate(exp, createProperties());
    }

    @Override
    protected void configurePredicate(Predicate predicate) {
        configureNamespaceAware(predicate);
    }

    @Override
    protected void configureExpression(Expression expression) {
        configureNamespaceAware(expression);
    }

    protected void configureNamespaceAware(Object builder) {
        if (definition.getNamespaces() != null && builder instanceof NamespaceAware) {
            NamespaceAware namespaceAware = (NamespaceAware) builder;
            namespaceAware.setNamespaces(definition.getNamespaces());
        }
    }

    protected Object[] createProperties() {
        Object[] properties = new Object[3];
        properties[0] = definition.getResultType();
        properties[1] = parseString(definition.getHeaderName());
        properties[2] = parseString(definition.getPropertyName());
        return properties;
    }

    @Override
    protected void configureLanguage(Language language) {
        if (definition.getResultType() == null && definition.getResultTypeName() != null) {
            try {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(definition.getResultTypeName());
                definition.setResultType(clazz);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        if (definition.getResultType() == null && definition.getType() != null) {
            try {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(definition.getType());
                definition.setResultType(clazz);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        if (definition.getConfiguration() == null && definition.getConfigurationRef() != null) {
            definition.setConfiguration(mandatoryLookup(definition.getConfigurationRef(), Object.class));
        }
    }

}
