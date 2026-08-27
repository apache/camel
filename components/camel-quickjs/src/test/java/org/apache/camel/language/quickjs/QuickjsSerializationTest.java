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

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuickjsSerializationTest {

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

    @Test
    void inputStreamBodyIsRejectedAndNotConsumed() throws Exception {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream body = new ByteArrayInputStream(payload);
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);

        assertThatThrownBy(() -> language().createExpression("body").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("Streaming type");

        assertThat(body.readAllBytes()).isEqualTo(payload);
        assertThat(exchange.getMessage().getBody()).isSameAs(body);
    }

    @Test
    void readerBodyIsRejectedAndNotConsumed() throws Exception {
        StringReader body = new StringReader("hello");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);

        assertThatThrownBy(() -> language().createExpression("body").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("Streaming type");

        char[] buffer = new char[5];
        assertThat(body.read(buffer)).isEqualTo(5);
        assertThat(new String(buffer)).isEqualTo("hello");
    }

    @Test
    void streamCacheBodyIsRejectedAndNotConsumed() {
        byte[] payload = "cached".getBytes(StandardCharsets.UTF_8);
        InputStreamCache body = new InputStreamCache(payload);
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);

        assertThatThrownBy(() -> language().createExpression("body").evaluate(exchange, Object.class))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("Streaming type");

        assertThat(body.length()).isEqualTo(payload.length);
        assertThat(exchange.getMessage().getBody()).isSameAs(body);
    }

    @Test
    void byteArrayBodyIsBase64StringNotNumberArray() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("hi".getBytes(StandardCharsets.UTF_8));
        assertThat(language().createExpression("typeof body").evaluate(exchange, String.class)).isEqualTo("string");
        assertThat(language().createExpression("Array.isArray(body)").evaluate(exchange, Boolean.class)).isFalse();
    }

    @Test
    void nonSerializableHeaderIsOmitted() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("x");
        exchange.getMessage().setHeader("ok", "yes");
        exchange.getMessage().setHeader("bad", getClass());
        Language language = language();
        assertThat(language.createExpression("headers.ok").evaluate(exchange, String.class)).isEqualTo("yes");
        assertThat(language.createExpression("'bad' in headers").evaluate(exchange, Boolean.class)).isFalse();
        assertThat(exchange.getMessage().getHeader("bad")).isSameAs(getClass());
    }

    @Test
    void undefinedResultIsNull() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(language().createExpression("void 0").evaluate(exchange, Object.class)).isNull();
    }
}
