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
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.commons.codec.binary.Base64;

public class Base64ExpressionBuilder {

    private static final int LINE_LENGTH = Base64.MIME_CHUNK_SIZE;

    /**
     * Base 64 encodes the given expression
     */
    public static Expression base64encode(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                byte[] value;
                if (exp != null) {
                    value = exp.evaluate(exchange, byte[].class);
                } else {
                    value = exchange.getMessage().getBody(byte[].class);
                }
                if (value != null) {
                    Base64 base64 = Base64.builder().setLineLength(LINE_LENGTH).get();
                    return base64.encodeAsString(value);
                }
                return null;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "base64encode(" + expression + ")";
                } else {
                    return "base64encode()";
                }
            }
        };
    }

    /**
     * Base 64 decodes the given expression
     */
    public static Expression base64decode(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                byte[] value;
                if (exp != null) {
                    value = exp.evaluate(exchange, byte[].class);
                } else {
                    value = exchange.getMessage().getBody(byte[].class);
                }
                if (value != null) {
                    Base64 base64 = Base64.builder().setLineLength(LINE_LENGTH).get();
                    return base64.decode(value);
                }
                return null;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "base64decode(" + expression + ")";
                } else {
                    return "base64decode()";
                }
            }
        };
    }

}
