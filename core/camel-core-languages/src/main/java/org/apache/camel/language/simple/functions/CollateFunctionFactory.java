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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.language.simple.ast.SimpleFunctionExpression.ifStartsWithReturnRemainder;

/**
 * Built-in Simple function: {@code ${collate(group)}}.
 */
public final class CollateFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("collate(", function);
        if (remainder == null) {
            return null;
        }
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException("Valid syntax: ${collate(group)} was: " + function, index);
        }
        return SimpleExpressionBuilder.collateExpression("${body}", values.trim());
    }

    @Override
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("collate(", function);
        if (remainder == null) {
            return null;
        }
        String values = StringHelper.beforeLast(remainder, ")");
        if (values == null || ObjectHelper.isEmpty(values)) {
            throw new SimpleParserException("Valid syntax: ${collate(group)} was: " + function, index);
        }
        return "collate(exchange, " + values.trim() + ")";
    }
}
