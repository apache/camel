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
package org.apache.camel.language.simple.functions;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.SimpleExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple functions for date/time: {@code ${date:command}}, {@code ${date:command:pattern}},
 * {@code ${date-with-timezone:command:timezone:pattern}}.
 */
public final class DateFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("date-with-timezone:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 3);
            if (parts.length < 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, index);
            }
            return SimpleExpressionBuilder.dateExpression(parts[0], parts[1], parts[2]);
        }

        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length == 1) {
                return SimpleExpressionBuilder.dateExpression(parts[0]);
            } else {
                return SimpleExpressionBuilder.dateExpression(parts[0], parts[1]);
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        if ("date:millis".equals(function)) {
            return "System.currentTimeMillis()";
        }

        String remainder = ifStartsWithReturnRemainder("date-with-timezone:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 3);
            if (parts.length < 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, index);
            }
            return "date(exchange, \"" + parts[0] + "\", \"" + parts[1] + "\", \"" + parts[2] + "\")";
        }

        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length == 1) {
                return "date(exchange, \"" + parts[0] + "\")";
            } else {
                return "date(exchange, \"" + parts[0] + "\", null, \"" + parts[1] + "\")";
            }
        }

        return null;
    }
}
