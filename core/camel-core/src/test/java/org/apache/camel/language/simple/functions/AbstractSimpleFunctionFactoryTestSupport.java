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

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractSimpleFunctionFactoryTestSupport extends ExchangeTestSupport {

    protected abstract SimpleLanguageFunctionFactory createFactory();

    protected Object evaluate(String function) {
        return evaluate(function, Object.class);
    }

    protected <T> T evaluate(String function, Class<T> type) {
        Expression expression = createFactory().createFunction(context, function, 0);
        assertNotNull(expression, "No Expression could be created for function: " + function);
        expression.init(context);
        return expression.evaluate(exchange, type);
    }

    @SuppressWarnings("deprecation")
    protected String createCode(String function) {
        String code = createFactory().createCode(context, function, 0);
        assertNotNull(code, "No code could be created for function: " + function);
        return code;
    }
}
