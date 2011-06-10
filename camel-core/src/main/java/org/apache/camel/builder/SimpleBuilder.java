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
import org.apache.camel.Predicate;

/**
 * Creates an {@link org.apache.camel.language.Simple} language builder.
 * <p/>
 * This builder is available in the Java DSL from the {@link RouteBuilder} which means that using
 * simple language for {@link Expression}s or {@link Predicate}s is very easy with the help of this builder.
 *
 * @version 
 */
public class SimpleBuilder implements Predicate, Expression {

    private final String text;

    public SimpleBuilder(String text) {
        this.text = text;
    }

    public static SimpleBuilder simple(String text) {
        return new SimpleBuilder(text);
    }

    public boolean matches(Exchange exchange) {
        return exchange.getContext().resolveLanguage("simple").createPredicate(text).matches(exchange);
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        return exchange.getContext().resolveLanguage("simple").createExpression(text).evaluate(exchange, type);
    }

    public String toString() {
        return "Simple: " + text;
    }
}
