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
package org.apache.camel.language.python3;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3ErrorHandlingTest {

    static CamelContext context;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    static Language language() {
        return context.resolveLanguage("python3");
    }

    static Exchange exchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("hello");
        exchange.getIn().setHeader("foo", "bar");
        return exchange;
    }

    @Test
    void pythonParseErrorIsSyntaxException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("def (").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionIllegalSyntaxException.class);
    }

    @Test
    void pythonRuntimeErrorIsEvaluationException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("1 / 0").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .isNotInstanceOf(ExpressionIllegalSyntaxException.class);
    }

    @Test
    void missingNameIsEvaluationException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("not_defined").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void hostAccessDenialIsEvaluationException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("headers.put('k', 'v')").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .isNotInstanceOf(ExpressionIllegalSyntaxException.class);
    }
}
