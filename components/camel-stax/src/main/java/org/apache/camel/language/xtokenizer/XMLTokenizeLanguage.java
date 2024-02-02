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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputLanguageSupport;
import org.apache.camel.support.builder.Namespaces;

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
public class XMLTokenizeLanguage extends SingleInputLanguageSupport {

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        String headerName = property(String.class, properties, 0, getHeaderName());
        Character mode = property(Character.class, properties, 1, "i");
        Integer group = property(Integer.class, properties, 2, null);
        Object obj = properties[3];
        Namespaces ns = null;
        if (obj != null) {
            if (obj instanceof Namespaces) {
                ns = (Namespaces) obj;
            } else if (obj instanceof Map) {
                ns = new Namespaces();
                ((Map<String, String>) obj).forEach(ns::add);
            } else {
                throw new IllegalArgumentException(
                        "Namespaces is not instance of java.util.Map or " + Namespaces.class.getName());
            }
        }
        String propertyName = property(String.class, properties, 4, null);
        String variableName = property(String.class, properties, 5, null);

        XMLTokenExpressionIterator tokenizer = new XMLTokenExpressionIterator(expression, mode);
        if (headerName != null) {
            tokenizer.setHeaderName(headerName);
        }
        if (group != null) {
            tokenizer.setGroup(group);
        }
        if (ns != null) {
            tokenizer.setNamespaces(ns.getNamespaces());
        }
        if (propertyName != null) {
            tokenizer.setPropertyName(propertyName);
        }
        if (variableName != null) {
            tokenizer.setVariableName(variableName);
        }
        return tokenizer;
    }

}
