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
package org.apache.camel.component.regex;

import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

public class RegexExpression implements Expression {
    private final String regexString;
    private final Expression expression;

    public RegexExpression(String regexString, Expression expression) {
        this.regexString = regexString;
        this.expression = expression;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Predicate predicate = exchange1 -> Pattern.matches(regexString, expression.evaluate(exchange1, String.class));
        Boolean matches = predicate.matches(exchange);
        return exchange
                .getContext()
                .getTypeConverter()
                .convertTo(type, exchange, matches);
    }
}
