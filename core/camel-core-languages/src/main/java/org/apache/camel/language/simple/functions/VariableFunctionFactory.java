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
import static org.apache.camel.language.simple.SimpleFunctionHelper.parseVariable;

/**
 * Built-in Simple functions for variables: {@code ${variable.name}}, {@code ${variableAs(key, type)}},
 * {@code ${variables}}, {@code ${variables.size}}, etc.
 */
public final class VariableFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        // variableAs
        String remainder = ifStartsWithReturnRemainder("variableAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, index);
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type) || ObjectHelper.isNotEmpty(remainder)) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            type = StringHelper.removeQuotes(type);
            return ExpressionBuilder.variableExpression(key, type);
        }

        // variables exact matches (must check before parseVariable to avoid mis-routing)
        if ("variables".equals(function)) {
            return ExpressionBuilder.variablesExpression();
        } else if ("variables.size".equals(function) || "variables.size()".equals(function)
                || "variables.length".equals(function) || "variables.length()".equals(function)) {
            return ExpressionBuilder.variablesSizeExpression();
        }

        // variable function (variable.name, variables.name, etc.)
        remainder = parseVariable(function);
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
                throw new SimpleParserException("Valid syntax: ${variable.name[key]} was: " + function, index);
            }

            if (OgnlHelper.isValidOgnlExpression(key)) {
                return OgnlExpressionBuilder.variablesOgnlExpression(key);
            } else {
                return ExpressionBuilder.variableExpression(key);
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        // variableAs
        String remainder = ifStartsWithReturnRemainder("variableAs(", function);
        if (remainder != null) {
            String keyAndType = StringHelper.before(remainder, ")");
            if (keyAndType == null) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, index);
            }
            String key = StringHelper.before(keyAndType, ",");
            String type = StringHelper.after(keyAndType, ",");
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(type)) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "variableAs(exchange, \"" + key + "\", " + type + ")" + ognlCodeMethods(remainder, type);
        }

        // variables exact matches (must check before variable prefix)
        if ("variables".equals(function)) {
            return "variables(exchange)";
        } else if ("variables.size".equals(function) || "variables.size()".equals(function)
                || "variables.length".equals(function) || "variables.length()".equals(function)) {
            return "variablesSize(exchange)";
        }

        // variable (note: only matches "variable" prefix, not "variables" — preserving original asymmetry)
        remainder = ifStartsWithReturnRemainder("variable", function);
        if (remainder != null) {
            if (remainder.startsWith(".") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }
            String key = StringHelper.removeLeadingAndEndingQuotes(remainder);
            key = key.trim();

            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(key);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${variable.name[key]} was: " + function, index);
            }

            String idx = null;
            if (key.endsWith("]")) {
                idx = StringHelper.between(key, "[", "]");
                if (idx != null) {
                    key = StringHelper.before(key, "[");
                }
            }
            if (idx != null) {
                idx = StringHelper.removeLeadingAndEndingQuotes(idx);
                return "variableAsIndex(exchange, Object.class, \"" + key + "\", \"" + idx + "\")";
            } else if (OgnlHelper.isValidOgnlExpression(remainder)) {
                throw new SimpleParserException("Valid syntax: ${variableAs(key, type)} was: " + function, index);
            } else {
                return "variable(exchange, \"" + key + "\")";
            }
        }

        return null;
    }
}
