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
 *     <li>inject - injecting the contextual namespace bindings into the extracted token</li>
 *     <li>wrap - wrapping the extracted token in its ancestor context</li>
 *     <li>unwrap - unwrapping the extracted token to its child content</li>
 * </ul>
 */
public class XMLTokenizeLanguage extends LanguageSupport {

    private String path;
    private String headerName;
    private char mode;
    private int group;

    public static Expression tokenize(String path) {
        return tokenize(path, 'i');
    }

    public static Expression tokenize(String path, char mode) {
        XMLTokenizeLanguage language = new XMLTokenizeLanguage();
        language.setPath(path);
        language.setMode(mode);
        return language.createExpression(null);
    }

    public static Expression tokenize(String headerName, String path) {
        return tokenize(headerName, path, 'i');
    }

    public static Expression tokenize(String headerName, String path, char mode) {
        XMLTokenizeLanguage language = new XMLTokenizeLanguage();
        language.setHeaderName(headerName);
        language.setPath(path);
        language.setMode(mode);
        return language.createExpression(null);
    }

    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    /**
     * Creates a tokenize expression.
     */
    public Expression createExpression() {
        ObjectHelper.notNull(path, "path");

        Expression answer = ExpressionBuilder.tokenizeXMLAwareExpression(path, mode);
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

    public char getMode() {
        return mode;
    }

    public void setMode(char mode) {
        this.mode = mode;
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
