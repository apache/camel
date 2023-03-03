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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputLanguageSupport;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A language for tokenizer expressions.
 * <p/>
 * This xmltokenizer language can operate in the following modes:
 * <ul>
 * <li>i - injecting the contextual namespace bindings into the extracted token (default)</li>
 * <li>w - wrapping the extracted token in its ancestor context</li>
 * <li>u - unwrapping the extracted token to its child content</li>
 * <li>t - extracting the text content of the specified element</li>
 * </ul>
 */
@Language("xtokenize")
public class XMLTokenizeLanguage extends SingleInputLanguageSupport implements PropertyConfigurer {

    private String path;
    private char mode;
    private int group;
    private Namespaces namespaces;

    @Deprecated
    public static Expression tokenize(String path) {
        return tokenize(null, path, 'i');
    }

    @Deprecated
    public static Expression tokenize(String path, char mode) {
        return tokenize(null, path, mode);
    }

    @Deprecated
    public static Expression tokenize(String headerName, String path) {
        return tokenize(headerName, path, 'i');
    }

    @Deprecated
    public static Expression tokenize(String headerName, String path, char mode) {
        return tokenize(headerName, path, mode, 1, null);
    }

    @Deprecated
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
        XMLTokenExpressionIterator expr = new XMLTokenExpressionIterator(path, mode, group, getHeaderName(), getPropertyName());
        if (namespaces != null) {
            expr.setNamespaces(namespaces.getNamespaces());
        }
        return expr;
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        XMLTokenizeLanguage answer = new XMLTokenizeLanguage();
        answer.setHeaderName(property(String.class, properties, 0, getHeaderName()));
        answer.setMode(property(Character.class, properties, 1, "i"));
        answer.setGroup(property(Integer.class, properties, 2, group));
        Object obj = properties[3];
        if (obj != null) {
            if (obj instanceof Namespaces) {
                answer.setNamespaces((Namespaces) obj);
            } else if (obj instanceof Map) {
                Namespaces ns = new Namespaces();
                ((Map<String, String>) obj).forEach(ns::add);
                answer.setNamespaces(ns);
            } else {
                throw new IllegalArgumentException(
                        "Namespaces is not instance of java.util.Map or " + Namespaces.class.getName());
            }
        }
        String path = expression != null ? expression : this.path;
        answer.setPropertyName(property(String.class, properties, 4, getPropertyName()));
        return answer.createExpression(path);
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
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "headername":
            case "headerName":
                setHeaderName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "propertyname":
            case "propertyName":
                setPropertyName(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "mode":
                setMode(PropertyConfigurerSupport.property(camelContext, char.class, value));
                return true;
            case "group":
                setGroup(PropertyConfigurerSupport.property(camelContext, int.class, value));
                return true;
            case "namespaces":
                setNamespaces(PropertyConfigurerSupport.property(camelContext, Namespaces.class, value));
                return true;
            default:
                return false;
        }
    }
}
