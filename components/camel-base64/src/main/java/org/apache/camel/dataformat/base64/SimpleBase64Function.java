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
package org.apache.camel.dataformat.base64;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

import static org.apache.camel.language.simple.ast.SimpleFunctionExpression.codeSplitSafe;
import static org.apache.camel.language.simple.ast.SimpleFunctionExpression.ifStartsWithReturnRemainder;

@JdkService(SimpleLanguageFunctionFactory.FACTORY + "/camel-base64")
public class SimpleBase64Function implements SimpleLanguageFunctionFactory {

    private static final String ENCODE_CODE
            = """
                            byte[] data;
                            if (value != null) {
                                data = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, value);
                            } else {
                                data = exchange.getMessage().getBody(byte[].class);
                            }
                            if (data != null) {
                                org.apache.commons.codec.binary.Base64 base64
                                        = org.apache.commons.codec.binary.Base64.builder().setLineLength(org.apache.commons.codec.binary.Base64.MIME_CHUNK_SIZE).get();
                                return base64.encodeAsString(data);
                            }
                            return null
                    """;

    private static final String DECODE_CODE
            = """
                            byte[] data;
                            if (value != null) {
                                data = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, value);
                            } else {
                                data = exchange.getMessage().getBody(byte[].class);
                            }
                            if (data != null) {
                                org.apache.commons.codec.binary.Base64 base64
                                        = org.apache.commons.codec.binary.Base64.builder().setLineLength(org.apache.commons.codec.binary.Base64.MIME_CHUNK_SIZE).get();
                                return base64.decode(data);
                            }
                            return null
                    """;

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("base64Encode(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return Base64ExpressionBuilder.base64encode(exp);
        }

        remainder = ifStartsWithReturnRemainder("base64Decode(", function);
        if (remainder != null) {
            String exp = null;
            String value = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(value)) {
                exp = StringHelper.removeQuotes(value);
            }
            return Base64ExpressionBuilder.base64decode(exp);
        }

        return null;
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("base64Encode(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${base64Encode(exp)} was: " + function, index);
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "null";
            }
            return "Object value = " + exp + ";\n" + ENCODE_CODE;
        }

        remainder = ifStartsWithReturnRemainder("base64Decode(", function);
        if (remainder != null) {
            String exp = null;
            String values = StringHelper.beforeLast(remainder, ")");
            if (ObjectHelper.isNotEmpty(values)) {
                String[] tokens = codeSplitSafe(values, ',', true, true);
                if (tokens.length != 1) {
                    throw new SimpleParserException(
                            "Valid syntax: ${base64Decode(exp)} was: " + function, index);
                }
                // single quotes should be double quotes
                String s = tokens[0];
                if (StringHelper.isSingleQuoted(s)) {
                    s = StringHelper.removeLeadingAndEndingQuotes(s);
                    s = StringQuoteHelper.doubleQuote(s);
                }
                exp = s;
            }
            if (ObjectHelper.isEmpty(exp)) {
                exp = "null";
            }
            return "Object value = " + exp + ";\n" + DECODE_CODE;
        }

        return null;
    }

}
