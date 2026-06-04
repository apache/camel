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
import org.apache.camel.language.simple.MiscExpressionBuilder;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.SimpleFunctionHelper.appendClass;
import static org.apache.camel.language.simple.SimpleFunctionHelper.codeSplitSafe;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ognlCodeMethods;

/**
 * Built-in Simple miscellaneous functions: {@code ${isEmpty}}, {@code ${isAlpha}}, {@code ${isAlphaNumeric}},
 * {@code ${isNumeric}}, {@code ${not}}, {@code ${kindOfType}}, {@code ${throwException}}, {@code ${assert}},
 * {@code ${convertTo}}, {@code ${messageHistory}}, {@code ${uuid}}, {@code ${hash}}, {@code ${empty}},
 * {@code ${newEmpty}}, {@code ${iif}}, {@code ${load}}.
 *
 * <p>
 * Note: the {@code ${not}} CSimple code-generation path requires access to the {@code skipFileFunctions} flag from the
 * enclosing parser context and is therefore kept in {@code SimpleFunctionExpression} rather than here.
 */
public final class MiscFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("isEmpty(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isEmptyExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isAlpha(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isAlphaExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isAlphaNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isAlphaNumericExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("isNumeric(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.isNumericExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("not(", function);
        if (remainder != null) {
            String exp = "${body}";
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = value;
            }
            return MiscExpressionBuilder.isNotPredicate(exp);
        }

        remainder = ifStartsWithReturnRemainder("kindOfType(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return MiscExpressionBuilder.kindOfTypeExpression(exp);
        }

        remainder = ifStartsWithReturnRemainder("throwException(", function);
        if (remainder != null) {
            String msg;
            String type = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${throwException(msg)} or ${throwException(type,msg)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${throwException(msg)} or ${throwException(type,msg)} was: " + function, index);
                }
                msg = StringHelper.removeQuotes(tokens[0]);
                type = StringHelper.removeQuotes(tokens[1]);
            } else {
                msg = StringHelper.removeQuotes(values.trim());
            }
            return MiscExpressionBuilder.throwExceptionExpression(msg, type);
        }

        remainder = ifStartsWithReturnRemainder("assert(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length != 2) {
                throw new SimpleParserException("Valid syntax: ${assert(exp,msg)} was: " + function, index);
            }
            return MiscExpressionBuilder.assertExpression(tokens[0], StringHelper.removeQuotes(tokens[1]));
        }

        remainder = ifStartsWithReturnRemainder("convertTo(", function);
        if (remainder != null) {
            String exp = "${body}";
            String type;
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
                }
                exp = StringHelper.removeQuotes(tokens[0]);
                type = StringHelper.removeQuotes(tokens[1]);
            } else {
                type = StringHelper.removeQuotes(values.trim());
            }
            remainder = StringHelper.after(remainder, ")");
            if (ObjectHelper.isNotEmpty(remainder)) {
                boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
                if (invalid) {
                    throw new SimpleParserException(
                            "Valid syntax: ${convertTo(type).OGNL} or ${convertTo(exp,type).OGNL} was: " + function, index);
                }
                return MiscExpressionBuilder.convertToOgnlExpression(exp, type, remainder);
            } else {
                return MiscExpressionBuilder.convertToExpression(exp, type);
            }
        }

        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.parseBoolean(values);
            }
            return MiscExpressionBuilder.messageHistoryExpression(detailed);
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return MiscExpressionBuilder.messageHistoryExpression(true);
        }

        remainder = ifStartsWithReturnRemainder("uuid", function);
        if (remainder != null) {
            String values = StringHelper.between(remainder, "(", ")");
            return MiscExpressionBuilder.uuidExpression(values);
        } else if (ObjectHelper.equal(function, "uuid")) {
            return MiscExpressionBuilder.uuidExpression(null);
        }

        remainder = ifStartsWithReturnRemainder("hash(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
            }
            if (values.contains(",")) {
                String[] tokens = values.split(",", 2);
                if (tokens.length > 2) {
                    throw new SimpleParserException(
                            "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
                }
                return MiscExpressionBuilder.hashExpression(tokens[0].trim(), tokens[1].trim());
            } else {
                return MiscExpressionBuilder.hashExpression(values.trim(), "SHA-256");
            }
        }

        remainder = ifStartsWithReturnRemainder("empty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${empty(<type>)} but was: " + function, index);
            }
            return MiscExpressionBuilder.newEmptyExpression(value);
        }

        remainder = ifStartsWithReturnRemainder("newEmpty(", function);
        if (remainder != null) {
            String value = StringHelper.before(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${newEmpty(<type>)} but was: " + function, index);
            }
            return MiscExpressionBuilder.newEmptyExpression(value);
        }

        remainder = ifStartsWithReturnRemainder("iif(", function);
        if (remainder != null) {
            String values = StringHelper.beforeLast(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', true, true);
            if (tokens.length > 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
            }
            return MiscExpressionBuilder.iifExpression(tokens[0].trim(), tokens[1].trim(), tokens[2].trim());
        }

        remainder = ifStartsWithReturnRemainder("load(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${load(name)} but was: " + function, index);
            }
            return MiscExpressionBuilder.loadExpression(StringHelper.removeQuotes(value));
        }

        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder;

        remainder = ifStartsWithReturnRemainder("kindOfType(", function);
        if (remainder != null) {
            return codeUnaryObject("kindOfType", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("isEmpty(", function);
        if (remainder != null) {
            return codeUnaryObject("isEmpty", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("isAlpha(", function);
        if (remainder != null) {
            return codeUnaryObject("isAlpha", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("isAlphaNumeric(", function);
        if (remainder != null) {
            return codeUnaryObject("isAlphaNumeric", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("isNumeric(", function);
        if (remainder != null) {
            return codeUnaryObject("isNumeric", remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("convertTo(", function);
        if (remainder != null) {
            return codeConvertTo(remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("throwException(", function);
        if (remainder != null) {
            return codeThrowException(remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("assertExpression(", function);
        if (remainder != null) {
            throw new UnsupportedOperationException("assertExpression is not supported in csimple language");
        }

        remainder = ifStartsWithReturnRemainder("messageHistory", function);
        if (remainder != null) {
            boolean detailed;
            String values = StringHelper.between(remainder, "(", ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                detailed = true;
            } else {
                detailed = Boolean.parseBoolean(values);
            }
            return "messageHistory(exchange, " + (detailed ? "true" : "false") + ")";
        } else if (ObjectHelper.equal(function, "messageHistory")) {
            return "messageHistory(exchange, true)";
        }

        remainder = ifStartsWithReturnRemainder("empty(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${empty(<type>)} but was: " + function, index);
            }
            return "newEmpty(exchange, " + StringQuoteHelper.doubleQuote(value) + ")";
        }

        remainder = ifStartsWithReturnRemainder("newEmpty(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${newEmpty(<type>)} but was: " + function, index);
            }
            return "newEmpty(exchange, " + StringQuoteHelper.doubleQuote(value) + ")";
        }

        remainder = ifStartsWithReturnRemainder("hash(", function);
        if (remainder != null) {
            return codeHash(remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("uuid", function);
        if (remainder == null && "uuid".equals(function)) {
            remainder = "(default)";
        }
        if (remainder != null) {
            return codeUuid(remainder, function);
        }

        remainder = ifStartsWithReturnRemainder("iif(", function);
        if (remainder != null) {
            return codeIif(remainder, function, index);
        }

        remainder = ifStartsWithReturnRemainder("load(", function);
        if (remainder != null) {
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isEmpty(value)) {
                throw new SimpleParserException("Valid syntax: ${load(name)} but was: " + function, index);
            }
            if (StringHelper.isSingleQuoted(value)) {
                value = StringHelper.removeLeadingAndEndingQuotes(value);
                value = StringQuoteHelper.doubleQuote(value);
            }
            return "Object o = " + value + ";\n        return load(exchange, o);";
        }

        return null;
    }

    private static String codeUnaryObject(String name, String remainder, String function, int index) {
        String exp = null;
        String values = StringHelper.beforeLast(remainder, ")");
        if (ObjectHelper.isNotEmpty(values)) {
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length != 1) {
                throw new SimpleParserException("Valid syntax: ${" + name + "(exp)} was: " + function, index);
            }
            String s = tokens[0];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
            }
            exp = s;
        }
        if (ObjectHelper.isEmpty(exp)) {
            exp = "body";
        }
        return "Object o = " + exp + ";\n        return " + name + "(exchange, o);";
    }

    private static String codeConvertTo(String remainder, String function, int index) {
        String ognl = null;
        String exp = "body";
        String type;
        String values = StringHelper.before(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
        }
        if (values.contains(",")) {
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type)} or ${convertTo(exp,type)} was: " + function, index);
            }
            String s = tokens[0].trim();
            s = StringHelper.removeLeadingAndEndingQuotes(s);
            s = StringQuoteHelper.doubleQuote(s);
            exp = s;
            type = tokens[1];
        } else {
            type = values.trim();
        }
        type = appendClass(type);
        type = type.replace('$', '.');
        if (ObjectHelper.isEmpty(exp)) {
            exp = "null";
        }

        remainder = StringHelper.after(remainder, ")");
        if (ObjectHelper.isNotEmpty(remainder)) {
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException(
                        "Valid syntax: ${convertTo(type).OGNL} or ${convertTo(exp,type).OGNL} was: " + function, index);
            }
            ognl = ognlCodeMethods(remainder, type);
        }

        String code = "Object value = " + exp + ";\n        return convertTo(exchange, " + type + ", value)";
        if (ognl != null) {
            code += ognl;
        }
        code += ";";
        return code;
    }

    private static String codeThrowException(String remainder, String function, int index) {
        String msg;
        String type = "IllegalArgumentException";
        String values = StringHelper.before(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function, index);
        }
        if (values.contains(",")) {
            String[] tokens = codeSplitSafe(values, ',', true, true);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${throwException(msg)} or ${throwException(msg,type)} was: " + function, index);
            }
            msg = tokens[0];
            type = tokens[1];
        } else {
            msg = values.trim();
        }
        msg = StringHelper.removeLeadingAndEndingQuotes(msg);
        msg = StringQuoteHelper.doubleQuote(msg);
        type = appendClass(type);
        type = type.replace('$', '.');
        return "return throwException(exchange, " + msg + ", " + type + ");";
    }

    private static String codeHash(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
        }
        String[] tokens = codeSplitSafe(values, ',', true, true);
        if (tokens.length > 2) {
            throw new SimpleParserException(
                    "Valid syntax: ${hash(value,algorithm)} or ${hash(value)} was: " + function, index);
        }
        for (int i = 0; i < tokens.length; i++) {
            String s = tokens[i];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
                tokens[i] = s;
            }
        }
        String algo = "\"SHA-256\"";
        if (tokens.length == 2) {
            algo = tokens[1];
            if (!StringHelper.isQuoted(algo)) {
                algo = StringQuoteHelper.doubleQuote(algo);
            }
        }
        return "var val = " + tokens[0] + ";\n        return hash(exchange, val, " + algo + ");";
    }

    private static String codeUuid(String remainder, String function) {
        String generator = StringHelper.between(remainder, "(", ")");
        if (generator == null) {
            generator = "default";
        }
        StringBuilder sb = new StringBuilder(128);
        if ("classic".equals(generator)) {
            sb.append("    UuidGenerator uuid = new org.apache.camel.support.ClassicUuidGenerator();\n");
            sb.append("return uuid.generateUuid();");
        } else if ("short".equals(generator)) {
            sb.append("    UuidGenerator uuid = new org.apache.camel.support.ShortUuidGenerator();\n");
            sb.append("return uuid.generateUuid();");
        } else if ("simple".equals(generator)) {
            sb.append("    UuidGenerator uuid = new org.apache.camel.support.SimpleUuidGenerator();\n");
            sb.append("return uuid.generateUuid();");
        } else if ("default".equals(generator)) {
            sb.append("    UuidGenerator uuid = new org.apache.camel.support.DefaultUuidGenerator();\n");
            sb.append("return uuid.generateUuid();");
        } else if ("random".equals(generator)) {
            sb.append("    UuidGenerator uuid = new org.apache.camel.support.RandomUuidGenerator();\n");
            sb.append("return uuid.generateUuid();");
        } else {
            generator = StringQuoteHelper.doubleQuote(generator);
            sb.append("if (uuid == null) uuid = customUuidGenerator(exchange, ").append(generator)
                    .append("); return uuid.generateUuid();");
        }
        return sb.toString();
    }

    private static String codeIif(String remainder, String function, int index) {
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException(
                    "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
        }
        String[] tokens = codeSplitSafe(values, ',', true, true);
        if (tokens.length != 3) {
            throw new SimpleParserException(
                    "Valid syntax: ${iif(predicate,trueExpression,falseExpression)} was: " + function, index);
        }
        for (int i = 0; i < 3; i++) {
            String s = tokens[i];
            if (StringHelper.isSingleQuoted(s)) {
                s = StringHelper.removeLeadingAndEndingQuotes(s);
                s = StringQuoteHelper.doubleQuote(s);
                tokens[i] = s;
            }
        }
        return "Object o = " + tokens[0]
               + ";\n        boolean b = convertTo(exchange, boolean.class, o);\n        return b ? "
               + tokens[1] + " : " + tokens[2];
    }
}
