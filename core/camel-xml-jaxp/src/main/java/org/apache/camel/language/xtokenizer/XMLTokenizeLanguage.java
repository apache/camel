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
package org.apache.camel.language.xtokenizer;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.builder.Namespaces;
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
@Language("xtokenize")
public class XMLTokenizeLanguage extends LanguageSupport {

    private String headerName;
    private String path;
    private char mode;
    private int group;
    private Namespaces namespaces;

    public static Expression tokenize(String path) {
        return tokenize(null, path, 'i');
    }

    public static Expression tokenize(String path, char mode) {
        return tokenize(null, path, mode);
    }

    public static Expression tokenize(String headerName, String path) {
        return tokenize(headerName, path, 'i');
    }

    public static Expression tokenize(String headerName, String path, char mode) {
        return tokenize(headerName, path, mode, 1, null);
    }

    public static Expression tokenize(String headerName, String path, char mode, int group, Namespaces namespaces) {
        XMLTokenizeLanguage language = new XMLTokenizeLanguage();
        language.setHeaderName(headerName);
        language.setMode(mode);
        language.setGroup(group);
        language.setNamespaces(namespaces);
        return language.createExpression(path);
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    /**
     * Creates a tokenize expression.
     */
    @Override
    public Expression createExpression(String expression) {
        String path = expression != null ? expression : this.path;
        ObjectHelper.notNull(path, "path");
        XMLTokenExpressionIterator expr = new XMLTokenExpressionIterator(path, mode, group, headerName);
        if (namespaces != null) {
            expr.setNamespaces(namespaces.getNamespaces());
        }
        return expr;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public Namespaces getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Namespaces namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
