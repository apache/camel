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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.util.StringHelper;

/**
 * A helper class for working with <a href="http://camel.apache.org/expression.html">expressions</a>.
 */
public final class ExpressionBuilder extends org.apache.camel.support.builder.ExpressionBuilder {

    // this class is included in camel-core to be backwards compatible by extending from camel-support

    /**
     * Utility classes should not have a public constructor.
     */
    private ExpressionBuilder() {
    }

    /**
     * Returns an {@link TokenPairExpressionIterator} expression
     */
    public static Expression tokenizePairExpression(String startToken, String endToken, boolean includeTokens) {
        return new TokenPairExpressionIterator(startToken, endToken, includeTokens);
    }

    /**
     * Returns an {@link TokenXMLExpressionIterator} expression
     */
    public static Expression tokenizeXMLExpression(String tagName, String inheritNamespaceTagName) {
        StringHelper.notEmpty(tagName, "tagName");
        return new TokenXMLExpressionIterator(tagName, inheritNamespaceTagName);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode) {
        return tokenizeXMLAwareExpression(null, path, mode, 1, null);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode, int group) {
        return tokenizeXMLAwareExpression(null, path, mode, group, null);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode, int group, Namespaces namespaces) {
        return tokenizeXMLAwareExpression(null, path, mode, group, namespaces);
    }

    public static Expression tokenizeXMLAwareExpression(String headerName, String path, char mode, int group, Namespaces namespaces) {
        StringHelper.notEmpty(path, "path");
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Language language = exchange.getContext().resolveLanguage("xtokenize");
                if (headerName != null) {
                    setProperty(language, "headerName", headerName);
                }
                if (mode != 'i') {
                    setProperty(language, "mode", mode);
                }
                if (group > 1) {
                    setProperty(language, "group", group);
                }
                if (namespaces != null) {
                    setProperty(language, "namespaces", namespaces);
                }
                setProperty(language, "path", path);
                return language.createExpression(null).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "xtokenize(" + path + ")";
            }
        };
    }

}
