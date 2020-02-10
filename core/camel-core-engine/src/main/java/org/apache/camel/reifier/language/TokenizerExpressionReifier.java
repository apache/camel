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
import org.apache.camel.language.tokenizer.TokenizeLanguage;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.support.ExpressionToPredicateAdapter;

public class TokenizerExpressionReifier extends ExpressionReifier<TokenizerExpression> {

    public TokenizerExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, (TokenizerExpression) definition);
    }

    @Override
    public Expression createExpression() {
        // special for new line tokens, if defined from XML then its 2
        // characters, so we replace that back to a single char
        String token = definition.getToken();
        if (token.startsWith("\\n")) {
            token = '\n' + token.substring(2);
        }

        TokenizeLanguage language = new TokenizeLanguage();
        language.setToken(parseString(token));
        language.setEndToken(parseString(definition.getEndToken()));
        language.setInheritNamespaceTagName(parseString(definition.getInheritNamespaceTagName()));
        language.setHeaderName(parseString(definition.getHeaderName()));
        language.setGroupDelimiter(parseString(definition.getGroupDelimiter()));
        if (definition.getRegex() != null) {
            language.setRegex(parseBoolean(definition.getRegex()));
        }
        if (definition.getXml() != null) {
            language.setXml(parseBoolean(definition.getXml()));
        }
        if (definition.getIncludeTokens() != null) {
            language.setIncludeTokens(parseBoolean(definition.getIncludeTokens()));
        }
        if (definition.getGroup() != null && !"0".equals(definition.getGroup())) {
            language.setGroup(parseString(definition.getGroup()));
        }

        if (definition.getSkipFirst() != null) {
            language.setSkipFirst(parseBoolean(definition.getSkipFirst()));
        }
        return language.createExpression();
    }

    @Override
    public Predicate createPredicate() {
        Expression exp = createExpression();
        return ExpressionToPredicateAdapter.toPredicate(exp);
    }

}
