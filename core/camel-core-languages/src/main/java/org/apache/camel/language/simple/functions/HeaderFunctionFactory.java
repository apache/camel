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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.OgnlExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.appendClass;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ognlCodeMethods;
import static org.apache.camel.language.simple.SimpleFunctionHelper.parseInHeader;
import static org.apache.camel.language.simple.SimpleFunctionHelper.splitOgnl;

/**
 * Built-in Simple functions for message headers: {@code ${header.name}}, {@code ${headerAs(key, type)}},
 * {@code ${headers}}, {@code ${headers.size}}, etc.
 */
public final class HeaderFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        // headerAs
        String remainder = ifStartsWithReturnRemainder("headerAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, index);
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.headerExpression(key, type);
        }

        // headers exact matches (must check before parseInHeader to avoid mis-routing)
        if ("in.headers".equals(function) || "headers".equals(function)) {
            return ExpressionBuilder.headersExpression();
        } else if ("headers.size".equals(function) || "headers.size()".equals(function)
                || "headers.length".equals(function) || "headers.length()".equals(function)) {
            return ExpressionBuilder.headersSizeExpression();
        }

        // in header function (header.name, in.header.name, headers.name, etc.)
        remainder = parseInHeader(function);
        if (remainder != null) {
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);

            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${header.name[key]} was: " + function, index);
            }

            if (OgnlHelper.isValidOgnlExpression(key)) {
                return OgnlExpressionBuilder.headersOgnlExpression(key);
            } else {
                return ExpressionBuilder.headerExpression(key);
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        // headerAsIndex
        String remainder = ifStartsWithReturnRemainder("headerAsIndex(", function);
        if (remainder != null) {
            String keyTypeAndIndex = StringHelper.before(remainder, ")");
            if (keyTypeAndIndex == null) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, index);
            }
            String[] parts = keyTypeAndIndex.split(",");
            if (parts.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, index);
            }
            String key = parts[0];
            String type = parts[1];
            String idx = parts[2];
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(idx)) {
                throw new SimpleParserException(
                        "Valid syntax: ${headerAsIndex(key, type, index)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            idx = StringHelper.removeQuotes(idx);
            idx = idx.trim();
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${headerAsIndex(key, type, index).OGNL} was: " + function, index);
                }
                return "headerAsIndex(message, " + type + ", \"" + key + "\", \"" + idx + "\")"
                       + ognlCodeMethods(remainder, type);
            } else {
                return "headerAsIndex(message, " + type + ", \"" + key + "\", \"" + idx + "\")";
            }
        }

        // headerAs
        remainder = ifStartsWithReturnRemainder("headerAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, index);
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "headerAs(message, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // headers exact matches (must check before parseInHeader)
        if ("in.headers".equals(function) || "headers".equals(function)) {
            return "message.getHeaders()";
        } else if ("headers.size".equals(function) || "headers.size()".equals(function)
                || "headers.length".equals(function) || "headers.length()".equals(function)) {
            return "message.getHeaders().size()";
        }

        // in header function
        remainder = parseInHeader(function);
        if (remainder != null) {
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${header.name[key]} was: " + function, index);
            }

            boolean hasIndex = false;
            List<String> parts = splitOgnl(key);
            if (!parts.isEmpty()) {
                String s = parts.get(0);
                int pos = s.indexOf('[');
                if (pos != -1) {
                    hasIndex = true;
                    String before = s.substring(0, pos);
                    String after = s.substring(pos);
                    parts.set(0, before);
                    parts.add(1, after);
                }
            }
            if (hasIndex) {
                String func = "headerAsIndex(\"" + parts.get(0) + "\", Object.class, \"" + parts.get(1) + "\")";
                if (parts.size() > 2) {
                    String last = String.join("", parts.subList(2, parts.size()));
                    if (!last.isEmpty()) {
                        func += "." + last;
                    }
                }
                return createCode(camelContext, func, index);
            } else if (OgnlHelper.isValidOgnlExpression(key)) {
                throw new SimpleParserException("Valid syntax: ${headerAs(key, type).OGNL} was: " + function, index);
            } else {
                return "header(message, \"" + key + "\")";
            }
        }

        return null;
    }
}
