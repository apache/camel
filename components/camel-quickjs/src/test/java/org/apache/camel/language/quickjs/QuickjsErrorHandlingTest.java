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
package org.apache.camel.language.quickjs;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class QuickjsErrorHandlingTest {

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
        return context.resolveLanguage("quickjs");
    }

    static Exchange exchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("hello");
        return exchange;
    }

    @Test
    void invalidJavaScriptIsCamelException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("function {{{").evaluate(exchange, Object.class))
                .isInstanceOfAny(ExpressionIllegalSyntaxException.class, ExpressionEvaluationException.class);
    }

    @Test
    void runtimeErrorIsEvaluationException() {
        Exchange exchange = exchange();
        assertThatThrownBy(() -> language().createExpression("notDefined").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void typeErrorOnNullBodyIsEvaluationException() {
        Exchange exchange = new DefaultExchange(context);
        assertThatThrownBy(() -> language().createExpression("body.toUpperCase()").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .isNotInstanceOf(ExpressionIllegalSyntaxException.class);
    }

    @Test
    void stderrFromFailedEvaluationDoesNotAccumulate() {
        Exchange exchange = exchange();
        Throwable first = catchThrowable(
                () -> language().createExpression("firstMissingName").evaluate(exchange, Object.class));
        Throwable second = catchThrowable(
                () -> language().createExpression("secondMissingName").evaluate(exchange, Object.class));
        assertThat(first).isInstanceOf(ExpressionEvaluationException.class);
        assertThat(second).isInstanceOf(ExpressionEvaluationException.class);
        assertThat(messageOf(second)).contains("secondMissingName");
        assertThat(messageOf(second)).doesNotContain("firstMissingName");
    }

    static String messageOf(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                text.append(current.getMessage());
            }
        }
        return text.toString();
    }
}
