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
package org.apache.camel.component.file;

import java.util.Comparator;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.Language;
import org.apache.camel.support.ObjectHelper;

/**
 * Default file sorter.
 */
public final class GenericFileDefaultSorter {

    private GenericFileDefaultSorter() {
    }

    /**
     * Returns a new sory by file language expression
     *
     * @param context the camel context
     * @param expression the file language expression
     * @param reverse true to reverse order
     * @return the comparator
     */
    public static Comparator<Exchange> sortByFileLanguage(CamelContext context, String expression, boolean reverse) {
        return sortByFileLanguage(context, expression, reverse, false, null);
    }

    /**
     * Returns a new sory by file language expression
     *
     * @param context the camel context
     * @param expression the file language expression
     * @param reverse true to reverse order
     * @param ignoreCase ignore case if comparing strings
     * @return the comparator
     */
    public static Comparator<Exchange> sortByFileLanguage(CamelContext context, String expression, boolean reverse, boolean ignoreCase) {
        return sortByFileLanguage(context, expression, reverse, ignoreCase, null);
    }

    /**
     * Returns a new sort by file language expression
     *
     * @param context the camel context
     * @param expression the file language expression
     * @param reverse true to reverse order
     * @param ignoreCase ignore case if comparing strings
     * @param nested nested comparator for sub group sorting, can be null
     * @return the comparator
     */
    public static Comparator<Exchange> sortByFileLanguage(final CamelContext context, final String expression, final boolean reverse, final boolean ignoreCase,
                                                          final Comparator<Exchange> nested) {

        // the expression should be enclosed by ${ }
        String text = expression;
        if (!expression.startsWith("${")) {
            text = "${" + text;
        }
        if (!expression.endsWith("}")) {
            text = text + "}";
        }
        Language language = context.resolveLanguage("file");
        final Expression exp = language.createExpression(text);

        return new Comparator<Exchange>() {
            public int compare(Exchange o1, Exchange o2) {
                Object result1 = exp.evaluate(o1, Object.class);
                Object result2 = exp.evaluate(o2, Object.class);
                int answer = ObjectHelper.compare(result1, result2, ignoreCase);
                // if equal then sub sort by nested comparator
                if (answer == 0 && nested != null) {
                    answer = nested.compare(o1, o2);
                }
                return reverse ? -1 * answer : answer;
            }

            public String toString() {
                return expression + (nested != null ? ";" + nested.toString() : "");
            }
        };
    }

}
