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
package org.apache.camel.converter;

import org.apache.camel.Converter;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;

/**
 * Some useful converters for Camel APIs such as to convert a {@link Predicate} or {@link Expression} to a
 * {@link Processor}
 */
@Converter(generateBulkLoader = true)
public final class CamelConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private CamelConverter() {
    }

    @Converter(order = 1)
    public static Processor toProcessor(final Expression expression) {
        return exchange -> {
            // the response from a expression should be set on OUT
            Object answer = expression.evaluate(exchange, Object.class);
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            out.setBody(answer);
        };
    }

    @Converter(order = 2)
    public static Processor toProcessor(final Predicate predicate) {
        return exchange -> {
            // the response from a predicate should be set on OUT
            boolean answer = predicate.matches(exchange);
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            out.setBody(answer);
        };
    }

}
