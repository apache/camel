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
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;

import static org.apache.camel.language.simple.SimpleFunctionHelper.ifStartsWithReturnRemainder;

/**
 * Built-in Simple functions for properties component and registry references: {@code ${propertiesExist:key}},
 * {@code ${propertiesExist:!key}}, {@code ${properties:key}}, {@code ${properties:key:default}}, {@code ${ref:name}}.
 */
public final class PropertiesFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("propertiesExist:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            String key = parts[0];
            boolean negate = key != null && key.startsWith("!");
            if (negate) {
                key = key.substring(1);
            }
            return ExpressionBuilder.propertiesComponentExist(key, negate);
        }

        remainder = ifStartsWithReturnRemainder("properties:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            String defaultValue = parts.length >= 2 ? parts[1] : null;
            return ExpressionBuilder.propertiesComponentExpression(parts[0], defaultValue);
        }

        remainder = ifStartsWithReturnRemainder("ref:", function);
        if (remainder != null) {
            return ExpressionBuilder.refExpression(remainder);
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("properties:", function);
        if (remainder != null) {
            String[] parts = remainder.split(":", 2);
            String key = parts[0].trim();
            if (parts.length >= 2) {
                return "properties(exchange, \"" + key + "\", \"" + parts[1].trim() + "\")";
            } else {
                return "properties(exchange, \"" + key + "\")";
            }
        }

        remainder = ifStartsWithReturnRemainder("ref:", function);
        if (remainder != null) {
            return "ref(exchange, \"" + remainder + "\")";
        }

        return null;
    }
}
