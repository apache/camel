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
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;

public class TokenizerExpressionReifier extends ExpressionReifier<TokenizerExpression> {

    public TokenizerExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (TokenizerExpression) definition);
    }

    protected Object[] createProperties() {
        Object[] properties = new Object[11];
        // special for new line tokens, if defined from XML then its 2
        // characters, so we replace that back to a single char
        String token = definition.getToken();
        if (token.startsWith("\\n")) {
            token = '\n' + token.substring(2);
        }
        properties[0] = parseString(token);
        properties[1] = parseString(definition.getEndToken());
        properties[2] = parseString(definition.getInheritNamespaceTagName());
        properties[3] = parseString(definition.getHeaderName());
        properties[4] = parseString(definition.getGroupDelimiter());
        properties[5] = parseBoolean(definition.getRegex());
        properties[6] = parseBoolean(definition.getXml());
        properties[7] = parseBoolean(definition.getIncludeTokens());
        properties[8] = parseString(definition.getGroup());
        properties[9] = parseBoolean(definition.getSkipFirst());
        properties[10] = parseString(definition.getPropertyName());
        return properties;
    }

    @Override
    public Predicate createPredicate() {
        return ExpressionToPredicateAdapter.toPredicate(createExpression());
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
