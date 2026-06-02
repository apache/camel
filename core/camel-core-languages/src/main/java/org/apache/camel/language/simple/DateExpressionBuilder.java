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
package org.apache.camel.language.simple;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageHelper;

/**
 * Expression builder for date/time functions used by the simple language.
 */
public final class DateExpressionBuilder {

    private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])([^+-]+)");

    private DateExpressionBuilder() {
    }

    public static Expression dateExpression(final String command) {
        return dateExpression(command, null, null);
    }

    public static Expression dateExpression(final String command, final String pattern) {
        return dateExpression(command, null, pattern);
    }

    public static Expression dateExpression(
            final String commandWithOffsets, final String timezone,
            final String pattern) {
        final String command = commandWithOffsets.split("[+-]", 2)[0].trim();
        final List<Long> offsets = LanguageHelper.captureOffsets(commandWithOffsets, OFFSET_PATTERN);

        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Date date = evalDate(exchange, command);
                Object answer = LanguageHelper.applyDateOffsets(date, offsets, pattern, timezone);
                if ("millis".equals(command) && answer instanceof Date d) {
                    answer = d.getTime();
                }
                return answer;
            }

            @Override
            public String toString() {
                if (timezone != null && pattern != null) {
                    return "date(" + commandWithOffsets + ":" + timezone + ":" + pattern + ")";
                } else if (pattern != null) {
                    return "date(" + commandWithOffsets + ":" + pattern + ")";
                } else {
                    return "date(" + commandWithOffsets + ")";
                }
            }
        };
    }

    private static Date evalDate(Exchange exchange, String command) {
        Date date;
        if ("now".equals(command) || "millis".equals(command)) {
            date = new Date();
        } else if ("exchangeCreated".equals(command)) {
            date = LanguageHelper.dateFromExchangeCreated(exchange);
        } else if (command.startsWith("header.")) {
            date = LanguageHelper.dateFromHeader(exchange, command, (e, o) -> tryConvertingAsDate(e, o, command));
        } else if (command.startsWith("variable.")) {
            date = LanguageHelper.dateFromVariable(exchange, command, (e, o) -> tryConvertingAsDate(e, o, command));
        } else if (command.startsWith("exchangeProperty.")) {
            date = LanguageHelper.dateFromExchangeProperty(exchange, command, (e, o) -> tryConvertingAsDate(e, o, command));
        } else if ("file".equals(command)) {
            date = LanguageHelper.dateFromFileLastModified(exchange, command);
        } else {
            throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
        }
        return date;
    }

    private static Date tryConvertingAsDate(Exchange exchange, Object obj, String command) {
        final Date date = exchange.getContext().getTypeConverter().tryConvertTo(Date.class, exchange, obj);
        if (date == null) {
            throw new IllegalArgumentException("Cannot find Date/long object at command: " + command);
        }

        return date;
    }
}
