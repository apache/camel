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
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.ExpressionToPredicateAdapter;

public class XMLTokenizerExpressionReifier extends ExpressionReifier<XMLTokenizerExpression> {

    public XMLTokenizerExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (XMLTokenizerExpression) definition);
    }

    @Override
    public Predicate createPredicate() {
        Expression exp = createExpression();
        return ExpressionToPredicateAdapter.toPredicate(exp);
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
        Object[] properties = new Object[5];
        properties[0] = parseString(definition.getHeaderName());
        properties[1] = parseString(definition.getMode());
        properties[2] = parseInt(definition.getGroup());
        properties[3] = definition.getNamespaces();
        properties[4] = parseString(definition.getPropertyName());
        return properties;
    }

}
