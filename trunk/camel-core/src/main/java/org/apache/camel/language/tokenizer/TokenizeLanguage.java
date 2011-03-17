/**
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
package org.apache.camel.language.tokenizer;

import org.apache.camel.Expression;
import org.apache.camel.IsSingleton;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;

/**
 * A language for tokenizer expressions.
 */
public class TokenizeLanguage implements Language, IsSingleton {

    private String token;
    private String headerName;
    private boolean regex;

    public static Expression tokenize(String token) {
        return tokenize(token, false);
    }

    public static Expression tokenize(String token, boolean regex) {
        TokenizeLanguage langugage = new TokenizeLanguage();
        langugage.setToken(token);
        langugage.setRegex(regex);
        return langugage.createExpression(null);
    }

    public static Expression tokenize(String headerName, String token) {
        return tokenize(headerName, token, false);
    }

    public static Expression tokenize(String headerName, String token, boolean regex) {
        TokenizeLanguage langugage = new TokenizeLanguage();
        langugage.setHeaderName(headerName);
        langugage.setToken(token);
        langugage.setRegex(regex);
        return langugage.createExpression(null);
    }

    public Predicate createPredicate(String expression) {
        return PredicateBuilder.toPredicate(createExpression(expression));
    }

    /**
     * Creates a tokenize expression.
     */
    public Expression createExpression() {
        ObjectHelper.notNull(token, "token");
        Expression exp = headerName == null ? ExpressionBuilder.bodyExpression() : ExpressionBuilder.headerExpression(headerName);
        if (regex) {
            return ExpressionBuilder.regexTokenizeExpression(exp, token);
        } else {
            return ExpressionBuilder.tokenizeExpression(exp, token);
        }
    }

    public Expression createExpression(String expression) {
        if (ObjectHelper.isNotEmpty(expression)) {
            this.token = expression;
        }
        return createExpression();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public boolean isSingleton() {
        return false;
    }
}