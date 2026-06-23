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
package org.apache.camel.jsoup;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.ExpressionAdapter;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class JSoupExpressionBuilder {

    /**
     * Cleans the HTML text
     */
    public static Expression clean(final String expression) {
        return new ExpressionAdapter() {

            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    return Jsoup.clean(value, Safelist.basic());
                }
                return null;
            }

            @Override
            public String toString() {
                return "htmlClean()";
            }
        };
    }

    /**
     * Parses the HTML text to org.jsoup.nodes.Document object.
     */
    public static Expression parse(final String expression) {
        return new ExpressionAdapter() {

            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    return Jsoup.parse(value);
                }
                return null;
            }

            @Override
            public String toString() {
                return "htmlParse()";
            }
        };
    }

    /**
     * Decodes the HTML to plain text that is suitable for LLMs
     */
    public static Expression decode(final String expression) {
        return new ExpressionAdapter() {

            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String value;
                if (exp != null) {
                    value = exp.evaluate(exchange, String.class);
                } else {
                    value = exchange.getMessage().getBody(String.class);
                }
                if (value != null) {
                    return Jsoup.parse(value).text();
                }
                return null;
            }

            @Override
            public String toString() {
                return "htmlDecode()";
            }
        };
    }

}
