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

package org.apache.camel.web.util;

import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.XPathExpression;

/**
 * 
 */
public class ExpressionRenderer {

    /**
     * a common render method to process the expressionDefinition
     * 
     * @param buffer
     * @param expression
     */
    public static void render(StringBuilder buffer, ExpressionDefinition expression) {

        if (buffer.toString().endsWith(")")) {
            buffer.append(".");
        }

        if (expression instanceof ExpressionClause) {
            renderExpressionClause(buffer, (ExpressionClause)expression);
        } else if (expression.getExpressionValue() instanceof ExpressionClause) {
            render(buffer, (ExpressionClause)expression.getExpressionValue());
        } else {
            if (expression.getExpressionValue() != null) {
                renderExpression(buffer, expression.getExpressionValue().toString());
            } else if (expression.getLanguage() != null) {
                renderLanguageExpression(buffer, expression);
            }
        }
    }

    /**
     * Render a constant: constant("")
     * 
     * @param expression
     * @param buffer
     */
    public static void renderConstant(StringBuilder buffer, ExpressionDefinition expression) {
        buffer.append(".constant(\"").append(expression.getExpressionValue().toString()).append("\")");
    }

    /**
     * Render an expression clause
     * 
     * @param buffer
     * @param expression
     */
    private static void renderExpressionClause(StringBuilder buffer, ExpressionClause expression) {
        if (expression.getLanguage() != null) {
            // render a language expression
            renderLanguageExpression(buffer, expression);
        } else if (expression.getExpressionType() instanceof MethodCallExpression) {
            // render a methodCall expression
            String exp = expression.getExpressionType().toString();
            String bean = exp.substring(exp.indexOf('{') + 1, exp.indexOf(','));
            String method = exp.substring(exp.indexOf('=') + 1, exp.indexOf('}'));
            buffer.append("method(\"").append(bean).append("\", \"").append(method).append("\")");
        } else if (expression.getExpressionType() instanceof XPathExpression) {
            XPathExpression xpath = (XPathExpression)expression.getExpressionType();
            buffer.append("xpath(\"").append(xpath.getExpression()).append("\", ").append(xpath.getResultType().getSimpleName()).append(".class)");
        } else {
            renderExpression(buffer, expression.getExpressionValue().toString());
        }
    }

    /**
     * Render a language expression
     * 
     * @param buffer
     * @param expression
     */
    public static void renderLanguageExpression(StringBuilder buffer, ExpressionDefinition expression) {
        // render a language expression
        buffer.append(expression.getLanguage()).append("(\"");
        if (expression.getExpression() != null) {
            buffer.append(expression.getExpression()).append("\")");
        } else if (expression.getExpressionValue() instanceof ExpressionClause) {
            buffer.append(((ExpressionClause)expression.getExpressionValue()).getExpression()).append("\")");
        }
    }

    /**
     * Render a simple expression: header(foo) -> header("foo")
     * tokenize(header(foo), ,) -> header("foo").tokenize(",")
     * 
     * @param buffer
     * @param expression
     */
    public static void renderExpression(StringBuilder buffer, String expression) {
        if (!expression.contains(",")) {
            // header(foo) -> header("foo")
            expression = expression.replaceAll("\\(", "(\"").replaceAll("\\)", "\")");
            buffer.append(expression);
        } else if (expression.startsWith("tokenize")) {
            String words[] = expression.split("\\(");
            if (words.length == 2) {
                // tokenize(body, ,) -> body().tokenize(",")
                String tokenize = words[1].substring(words[1].indexOf(" ") + 1, words[1].lastIndexOf(")"));
                words[1] = words[1].substring(0, words[1].indexOf(","));

                buffer.append(words[1]).append("().");
                buffer.append(words[0]).append("(\"").append(tokenize).append("\")");
            } else if (words.length == 3) {
                // tokenize(header(foo), ,) -> header("foo").tokenize(",")
                String symbolName = words[2].substring(0, words[2].indexOf(")"));
                String tokenize = words[2].substring(words[2].indexOf(" ") + 1, words[2].lastIndexOf(")"));

                buffer.append(words[1]).append("(\"").append(symbolName).append("\").");
                buffer.append(words[0]).append("(\"").append(tokenize).append("\")");
            }
        } else if (expression.startsWith("append")) {
            // append(body, World!) -> body().append(" World!")
            String words[] = expression.split("\\(|, |\\)");

            buffer.append(words[1]).append("().").append("append(\"").append(words[2]).append("\")");
        } else if (expression.startsWith("prepend")) {
            // prepend(body, World!) -> body().prepend(" World!")
            String words[] = expression.split("\\(|, |\\)");

            buffer.append(words[1]).append("().").append("prepend(\"").append(words[2]).append("\")");
        }
    }
}
