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
package org.apache.camel.util;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * A collection of helper methods for working with expressions.
 *
 * @version $Revision$
 */
public final class ExpressionHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ExpressionHelper() {
    }

    /**
     * Evaluates the given expression on the exchange as a String value
     *
     * @param expression the expression to evaluate
     * @param exchange the exchange to use to evaluate the expression
     * @return the result of the evaluation as a string.
     */
    public static <E extends Exchange> String evaluateAsString(Expression<E> expression, E exchange) {
        return evaluateAsType(expression, exchange, String.class);
    }

    /**
     * Evaluates the given expression on the exchange, converting the result to
     * the given type
     *
     * @param expression the expression to evaluate
     * @param exchange the exchange to use to evaluate the expression
     * @param resultType the type of the result that is required
     * @return the result of the evaluation as the specified type.
     */
    public static <T, E extends Exchange> T evaluateAsType(Expression<E> expression, E exchange,
                                                           Class<T> resultType) {
        Object value = expression.evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, value);
    }
}
