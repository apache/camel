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

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * A language for tokenizer expressions.
 * <p/>
 * This xmltokenizer language can operate in the following modes:
 * <ul>
 *     <li>wrap - wrapping the extracted token in its ancestor context</li>
 *     <li>injected - injecting the contextual namespace bindings into the extracted token</li>
 * </ul>
 */
public class XMLTokenizeLanguage extends LanguageSupport {

    private String path;
    private String headerName;
    private boolean wrap;
    private int group;

    public static Expression tokenize(String token) {
        return tokenize(token, false);
    }

    public static Expression tokenize(String token, boolean wrap) {
        XMLTokenizeLanguage language = new XMLTokenizeLanguage();
        language.setPath(token);
        language.setWrap(wrap);
        return language.createExpression(null);
    }

    public static Expression tokenize(String headerName, String token) {
        return tokenize(headerName, token, false);
    }

    public static Expression tokenize(String headerName, String token, boolean wrap) {
        XMLTokenizeLanguage language = new XMLTokenizeLanguage();
        language.setHeaderName(headerName);
        language.setPath(token);
        language.setWrap(wrap);
        return language.createExpression(null);
    }

    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    /**
     * Creates a tokenize expression.
     */
    public Expression createExpression() {
        ObjectHelper.notNull(path, "token");

        Expression answer = ExpressionBuilder.tokenizeXMLAwareExpression(path, wrap);

        // if group then wrap answer in group expression
        if (group > 0) {
            //REVISIT wrap the xml tokens with a group element to turn the result into xml?
            answer = ExpressionBuilder.groupIteratorExpression(answer, null, group);
        }

        return answer;
    }

    public Expression createExpression(String expression) {
        if (ObjectHelper.isNotEmpty(expression)) {
            this.path = expression;
        }
        return createExpression();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isWrap() {
        return wrap;
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }
    public int getGroup() {
        return group;
    }

    public void setGroup(int group) {
        this.group = group;
    }

    public boolean isSingleton() {
        return false;
    }
}
