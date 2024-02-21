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
 * Represents a boolean value.
 */
public class BooleanExpression extends BaseSimpleNode {

    private final boolean value;

    public BooleanExpression(SimpleToken token) {
        super(token);
        this.value = "true".equals(token.getText());
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) throws SimpleParserException {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                if (type == Object.class || type == Boolean.class || type == boolean.class) {
                    return (T) Boolean.valueOf(value);
                }
                return camelContext.getTypeConverter().tryConvertTo(type, exchange, value);
            }

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        };
    }

    @Override
    public String createCode(String expression) throws SimpleParserException {
        return value ? "true" : "false";
    }
}
