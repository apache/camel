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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.util.ObjectHelper;

/**
 * A useful base class for {@link Predicate} and {@link Expression} implementations
 */
public abstract class ExpressionSupport implements Expression, Predicate {

    @Override
    public void init(CamelContext context) {
    }

    @Override
    public boolean matches(Exchange exchange) {
        Object value = evaluate(exchange, Object.class);
        return ObjectHelper.evaluateValuePredicate(value);
    }

    public Object evaluate(Exchange exchange) {
        return evaluate(exchange, Object.class);
    }

    public void assertMatches(String text, Exchange exchange) {
        if (!matches(exchange)) {
            throw new AssertionError(text + " " + assertionFailureMessage(exchange) + " for exchange: " + exchange);
        }
    }

    protected abstract String assertionFailureMessage(Exchange exchange);
}
