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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.DateExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple functions for date/time: {@code ${date:command}}, {@code ${date:command:pattern}},
 * {@code ${date-with-timezone:command:timezone:pattern}}.
 */
public final class DateFunctionFactory implements SimpleLanguageFunctionFactory {

    private static final Pattern TIMEZONE_WITH_COLON = Pattern.compile("((?:GMT|UTC)?[+-]\\d{1,2}:\\d{2}):(.+)");

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("date-with-timezone:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 3);
            if (parts.length < 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${date-with-timezone:command:timezone:pattern} was: " + function, index);
            }
            // a timezone with an offset holds a colon itself, such as GMT+02:00
            Matcher offset = TIMEZONE_WITH_COLON.matcher(remainder.substring(parts[0].length() + 1));
            if (offset.matches()) {
                return DateExpressionBuilder.dateExpression(parts[0], offset.group(1), offset.group(2));
            }
            return DateExpressionBuilder.dateExpression(parts[0], parts[1], parts[2]);
        }

        remainder = ifStartsWithReturnRemainder("date:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            if (parts.length == 1) {
                return DateExpressionBuilder.dateExpression(parts[0]);
            } else {
                return DateExpressionBuilder.dateExpression(parts[0], parts[1]);
            }
        }

        return null;
    }
}
