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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void setResultIsList() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(language().createExpression("new Set([1, 'a', 2])").evaluate(exchange, Object.class))
                .isEqualTo(List.of(1, "a", 2));
    }

    @Test
    void mapResultKeepsEntries() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(language().createExpression("new Map([['a', 1], [2, new Set(['x'])]])").evaluate(exchange, Object.class))
                .isEqualTo(Map.of("a", 1, "2", List.of("x")));
    }

    @Test
    void typedArrayResultIsListOfNumbers() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(language().createExpression("new Uint8Array([1, 2, 255])").evaluate(exchange, Object.class))
                .isEqualTo(List.of(1, 2, 255));
        assertThat(language().createExpression("new Float64Array([1.5])").evaluate(exchange, Object.class))
                .isEqualTo(List.of(1.5));
        assertThat(language().createExpression("new Uint8Array([7, 8]).buffer").evaluate(exchange, Object.class))
                .isEqualTo(List.of(7, 8));
    }

    @Test
    void nestedSetMapAndTypedArrayAreConverted() {
        Exchange exchange = new DefaultExchange(context);
        Object result = language().createExpression(
                "({ tags: new Set(['a', 'b']), list: [new Map([['k', 'v']]), new Int8Array([-1])], n: 1, s: 'x' })")
                .evaluate(exchange, Object.class);
        assertThat(result).isEqualTo(Map.of(
                "tags", List.of("a", "b"),
                "list", List.of(Map.of("k", "v"), List.of(-1)),
                "n", 1,
                "s", "x"));
    }

    @Test
    void statementScriptConvertsSet() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(language().createExpression("const s = new Set([3, 4]); s;").evaluate(exchange, Object.class))
                .isEqualTo(List.of(3, 4));
    }

    @Test
    void scriptingEvaluateConvertsSet() {
        ScriptingLanguage scripting = (ScriptingLanguage) language();
        assertThat(scripting.evaluate("({ s: new Set([x, 2]) })", Map.of("x", 1), Object.class))
                .isEqualTo(Map.of("s", List.of(1, 2)));
    }

    @Test
    void plainResultsAndJsonRulesAreKept() {
        Exchange exchange = new DefaultExchange(context);
        Language language = language();
        assertThat(language.createExpression("({ a: [1, { b: 'c' }], d: 'e' })").evaluate(exchange, Object.class))
                .isEqualTo(Map.of("a", List.of(1, Map.of("b", "c")), "d", "e"));
        assertThat(language.createExpression("NaN").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("() => 1").evaluate(exchange, Object.class)).isNull();
        assertThat(language.createExpression("({ f: () => 1, u: undefined, n: NaN })").evaluate(exchange, Object.class))
                .isEqualTo(Collections.singletonMap("n", null));
        assertThat(language.createExpression("new Date(0)").evaluate(exchange, Object.class))
                .isEqualTo("1970-01-01T00:00:00.000Z");
        assertThat(language.createExpression("({ when: new Date(0), tags: new Set([1]) })").evaluate(exchange, Object.class))
                .isEqualTo(Map.of("when", "1970-01-01T00:00:00.000Z", "tags", List.of(1)));
    }

    @Test
    void cyclicResultWithSetDoesNotRecurseForever() {
        Exchange exchange = new DefaultExchange(context);
        // the cycle is left to JSON as before; the conversion must not follow it into a stack overflow
        assertThatCode(() -> language().createExpression("const o = { s: new Set([1]) }; o.self = o; o;")
                .evaluate(exchange, Object.class))
                .doesNotThrowAnyException();
    }
}
