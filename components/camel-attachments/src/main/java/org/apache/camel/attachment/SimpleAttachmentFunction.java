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
package org.apache.camel.attachment;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

@JdkService(SimpleLanguageFunctionFactory.FACTORY + "/camel-attachments")
public class SimpleAttachmentFunction implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        if ("attachments".equals(function)) {
            return AttachmentExpressionBuilder.attachments();
        } else if ("attachments.size".equals(function) || "attachments.size()".equals(function)
                || "attachments.length".equals(function) || "attachments.length()".equals(function)) {
            return AttachmentExpressionBuilder.attachmentsSize();
        }

        String remainder = ifStartsWithReturnRemainder("attachmentContent(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContent(key)} was: "
                                                + function,
                        index);
            }
            return AttachmentExpressionBuilder.attachmentContent(values, null);
        }
        remainder = ifStartsWithReturnRemainder("attachmentContentAs(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAs(key,type)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAs(key,type)} was: " + function, index);
            }
            String key = tokens[0];
            String type = tokens[1];
            return AttachmentExpressionBuilder.attachmentContent(key, type);
        }
        remainder = ifStartsWithReturnRemainder("attachmentContentAsText(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAsText(key)}} was: "
                                                + function,
                        index);
            }
            return AttachmentExpressionBuilder.attachmentContent(values, "String");
        }

        remainder = ifStartsWithReturnRemainder("attachmentHeaderAs(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeaderAs(key,name,type)} was: " + function, index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeaderAs(key,name,type)} was: " + function,
                        index);
            }
            String key = tokens[0];
            String name = tokens[1];
            String type = tokens[2];
            return AttachmentExpressionBuilder.attachmentContentHeader(key, name, type);
        }
        remainder = ifStartsWithReturnRemainder("attachmentHeader(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeader(key,name)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length != 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeader(key,name)} was: " + function,
                        index);
            }
            String key = tokens[0];
            String name = tokens[1];
            return AttachmentExpressionBuilder.attachmentContentHeader(key, name, null);
        }

        remainder = ifStartsWithReturnRemainder("attachmentContentType(", function);
        if (remainder != null) {
            String key = StringHelper.before(remainder, ")");
            if (key == null || ObjectHelper.isEmpty(key)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentType(key)} was: "
                                                + function,
                        index);
            }
            return AttachmentExpressionBuilder.attachmentContentType(key);
        }

        remainder = ifStartsWithReturnRemainder("attachment", function);
        if (remainder != null) {
            // remove leading character (dot, colon or ?)
            if (remainder.startsWith(".") || remainder.startsWith(":") || remainder.startsWith("?")) {
                remainder = remainder.substring(1);
            }
            // remove starting and ending brackets
            if (remainder.startsWith("[") && remainder.endsWith("]")) {
                remainder = remainder.substring(1, remainder.length() - 1);
            }

            // validate syntax
            boolean invalid = OgnlHelper.isInvalidValidOgnlExpression(remainder);
            if (invalid) {
                throw new SimpleParserException("Valid syntax: ${attachment.OGNL} was: " + function, index);
            }

            if (OgnlHelper.isValidOgnlExpression(remainder)) {
                // ognl based attachment
                return AttachmentExpressionBuilder.attachmentOgnlExpression(remainder);
            } else {
                // regular attachment
                return AttachmentExpressionBuilder.attachmentExpression(remainder);
            }
        }
        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        if ("attachments".equals(function)) {
            return "attachments(exchange)";
        } else if ("attachments.size".equals(function) || "attachments.size()".equals(function)
                || "attachments.length".equals(function) || "attachments.length()".equals(function)) {
            return "attachmentsSize(exchange)";
        }

        String remainder = ifStartsWithReturnRemainder("attachmentContent(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContent(key)} was: "
                                                + function,
                        index);
            }
            String key = StringHelper.removeQuotes(values);
            key = key.trim();
            return "attachmentContent(exchange, \"" + key + "\")";
        }
        remainder = ifStartsWithReturnRemainder("attachmentContentAs(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAs(key,type)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length > 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAs(key,type)} was: " + function, index);
            }
            String key = tokens[0];
            String type = tokens[1];
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "attachmentContentAs(exchange, \"" + key + "\", " + type + ")";
        }
        remainder = ifStartsWithReturnRemainder("attachmentContentAsText(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentAsText(key)}} was: "
                                                + function,
                        index);
            }
            String key = StringHelper.removeQuotes(values);
            key = key.trim();
            return "attachmentContentAsText(exchange, \"" + key + "\")";
        }
        remainder = ifStartsWithReturnRemainder("attachmentContentType(", function);
        if (remainder != null) {
            String key = StringHelper.before(remainder, ")");
            if (key == null || ObjectHelper.isEmpty(key)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentContentType(key)} was: "
                                                + function,
                        index);
            }
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            return "attachmentContentType(exchange, \"" + key + "\")";
        }
        remainder = ifStartsWithReturnRemainder("attachmentHeader(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeader(key,name)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length != 2) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeader(key,name)} was: " + function, index);
            }
            String key = tokens[0];
            String name = tokens[1];
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            name = StringHelper.removeQuotes(name);
            name = name.trim();
            return "attachmentHeader(exchange, \"" + key + "\", \"" + name + "\")";
        }
        remainder = ifStartsWithReturnRemainder("attachmentHeaderAs(", function);
        if (remainder != null) {
            String values = StringHelper.before(remainder, ")");
            if (values == null || ObjectHelper.isEmpty(values)) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeaderAs(key,name,type)} was: "
                                                + function,
                        index);
            }
            String[] tokens = StringQuoteHelper.splitSafeQuote(values, ',', false);
            if (tokens.length != 3) {
                throw new SimpleParserException(
                        "Valid syntax: ${attachmentHeaderAs(key,name,type)} was: " + function, index);
            }
            String key = tokens[0];
            String name = tokens[1];
            String type = tokens[2];
            key = StringHelper.removeQuotes(key);
            key = key.trim();
            name = StringHelper.removeQuotes(name);
            name = name.trim();
            type = appendClass(type);
            type = type.replace('$', '.');
            type = type.trim();
            return "attachmentHeaderAs(exchange, \"" + key + "\", \"" + name + "\", " + type + ")";
        }

        return null;
    }

    private String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        return null;
    }

    private static String appendClass(String type) {
        type = StringHelper.removeQuotes(type);
        if (!type.endsWith(".class")) {
            type = type + ".class";
        }
        return type;
    }

}
