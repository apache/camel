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
import org.apache.camel.spi.SimpleLanguageFunctionFactory;

import static org.apache.camel.language.simple.SimpleFunctionHelper.appendClass;
import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple function for type literal access: {@code ${type:fqn}}, {@code ${type:fqn.FIELD}}.
 *
 * <p>
 * The expression is eagerly initialized and cached because type references are constant.
 */
public final class TypeFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            Expression exp = MiscExpressionBuilder.typeExpression(remainder);
            exp.init(camelContext);
            return MiscExpressionBuilder.cacheExpression(exp);
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("type:", function);
        if (remainder != null) {
            int pos = remainder.lastIndexOf('.');
            String type = pos != -1 ? remainder.substring(0, pos) : remainder;
            String field = pos != -1 ? remainder.substring(pos + 1) : null;
            type = appendClass(type);
            type = type.replace('$', '.');
            if (field != null) {
                return "type(exchange, " + type + ", \"" + field + "\")";
            } else {
                return "type(exchange, " + type + ")";
            }
        }
        return null;
    }
}
