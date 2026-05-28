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
 * Built-in Simple functions for system properties and environment variables: {@code ${sys.name}},
 * {@code ${sysenv.name}}, {@code ${sysenv:name}}, {@code ${env.name}}, {@code ${env:name}}.
 */
public final class SystemFunctionFactory implements SimpleLanguageFunctionFactory {

    @Override
    public Expression createFunction(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("sys.", function);
        if (remainder != null) {
            return ExpressionBuilder.systemPropertyExpression(remainder);
        }

        remainder = ifStartsWithReturnRemainder("sysenv.", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("sysenv:", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env.", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env:", function);
        }
        if (remainder != null) {
            return ExpressionBuilder.systemEnvironmentExpression(remainder);
        }

        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String createCode(CamelContext camelContext, String function, int index) {
        String remainder = ifStartsWithReturnRemainder("sys.", function);
        if (remainder != null) {
            return "sys(\"" + remainder + "\")";
        }

        remainder = ifStartsWithReturnRemainder("sysenv.", function);
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("sysenv:", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env.", function);
        }
        if (remainder == null) {
            remainder = ifStartsWithReturnRemainder("env:", function);
        }
        if (remainder != null) {
            return "sysenv(\"" + remainder + "\")";
        }

        return null;
    }
}
