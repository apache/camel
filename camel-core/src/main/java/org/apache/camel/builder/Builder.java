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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

/**
 * A helper class for including portions of the <a
 * href="http://activemq.apache.org/camel/expression.html">expression</a> and
 * <a href="http://activemq.apache.org/camel/predicate.html">predicate</a> <a
 * href="http://activemq.apache.org/camel/dsl.html">Java DSL</a>
 *
 * @version $Revision$
 */
public final class Builder {

    /**
     * Utility classes should not have a public constructor.
     */
    private Builder() {
    }

    /**
     * Returns a constant expression
     */
    public static <E extends Exchange> ValueBuilder<E> constant(Object value) {
        Expression<E> expression = ExpressionBuilder.constantExpression(value);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for headers on an exchange
     */
    public static <E extends Exchange> ValueBuilder<E> header(String name) {
        Expression<E> expression = ExpressionBuilder.headerExpression(name);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public static <E extends Exchange> ValueBuilder<E> body() {
        Expression<E> expression = ExpressionBuilder.bodyExpression();
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     */
    public static <E extends Exchange, T> ValueBuilder<E> bodyAs(Class<T> type) {
        Expression<E> expression = ExpressionBuilder.<E, T> bodyExpression(type);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an
     * exchange
     */
    public static <E extends Exchange> ValueBuilder<E> outBody() {
        Expression<E> expression = ExpressionBuilder.outBodyExpression();
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a
     * specific type
     */
    public static <E extends Exchange, T> ValueBuilder<E> outBodyAs(Class<T> type) {
        Expression<E> expression = ExpressionBuilder.<E, T> outBodyExpression(type);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the fault body on an
     * exchange
     */
    public static <E extends Exchange> ValueBuilder<E> faultBody() {
        Expression<E> expression = ExpressionBuilder.faultBodyExpression();
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the fault message body as a
     * specific type
     */
    public static <E extends Exchange, T> ValueBuilder<E> faultBodyAs(Class<T> type) {
        Expression<E> expression = ExpressionBuilder.<E, T> faultBodyExpression(type);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns an expression for the given system property
     */
    public static <E extends Exchange> ValueBuilder<E> systemProperty(final String name) {
        return systemProperty(name, null);
    }

    /**
     * Returns an expression for the given system property
     */
    public static <E extends Exchange> ValueBuilder<E> systemProperty(final String name,
                                                                      final String defaultValue) {
        return new ValueBuilder<E>(ExpressionBuilder.<E> systemProperty(name, defaultValue));
    }
}
