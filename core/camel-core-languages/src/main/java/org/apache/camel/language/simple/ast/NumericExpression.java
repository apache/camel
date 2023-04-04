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
package org.apache.camel.language.simple.ast;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;

/**
 * Represents a numeric value.
 */
public class NumericExpression extends BaseSimpleNode {

    private final Object number;

    public NumericExpression(SimpleToken token, String text) {
        super(token);

        // what kind of numeric is it
        boolean dot = text.indexOf('.') != -1;
        if (dot) {
            number = Double.parseDouble(text);
        } else {
            // its either a long or integer value (lets just avoid bytes)
            long lon = Long.parseLong(text);
            if (lon < Integer.MAX_VALUE) {
                number = Integer.valueOf(text);
            } else {
                number = lon;
            }
        }
    }

    public Object getNumber() {
        return number;
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) throws SimpleParserException {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                if (type == Object.class || type == int.class || type == Integer.class
                        || type == long.class || type == Long.class
                        || type == double.class || type == Double.class) {
                    return type.cast(number);
                }
                return exchange.getContext().getTypeConverter().tryConvertTo(type, exchange, number);
            }

            @Override
            public String toString() {
                return String.valueOf(number);
            }
        };
    }

    @Override
    public String createCode(String expression) throws SimpleParserException {
        // Double, Long or Integer
        if (number instanceof Double) {
            return number + "d";
        } else if (number instanceof Long) {
            return number + "l";
        } else {
            return number.toString();
        }
    }
}
