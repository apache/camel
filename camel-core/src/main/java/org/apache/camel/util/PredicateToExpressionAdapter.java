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
import org.apache.camel.Predicate;

/**
 * To adapt {@link Predicate} as an {@link org.apache.camel.Expression}
 */
public final class PredicateToExpressionAdapter implements Expression {
    private final Predicate predicate;

    public PredicateToExpressionAdapter(Predicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        boolean matches = predicate.matches(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, exchange, matches);
    }

    @Override
    public String toString() {
        return predicate.toString();
    }

    /**
     * Converts the given predicate into an {@link org.apache.camel.Expression}
     */
    public static Expression toExpression(final Predicate predicate) {
        return new PredicateToExpressionAdapter(predicate);
    }
    
}