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
package org.apache.camel.groovy.extend;

import groovy.lang.Closure;

import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionSupport;

/**
 * Bridges a closure to ExpressionSupport
 */
class ClosureExpression extends ExpressionSupport {

    private final Closure<?> closure;

    ClosureExpression(Closure<?> closure) {
        this.closure = closure;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = ClosureSupport.call(closure, exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return closure.toString();
    }

}