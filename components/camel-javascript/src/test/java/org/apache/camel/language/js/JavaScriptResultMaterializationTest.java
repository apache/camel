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
package org.apache.camel.language.js;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Guest values are copied into plain Java types before the per-evaluation Context is closed, so JS objects and arrays
 * returned by a script remain usable by the rest of the route.
 */
@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class JavaScriptResultMaterializationTest {

    private CamelContext context;
    private JavaScriptLanguage language;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
        language = (JavaScriptLanguage) context.resolveLanguage("js");
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void objectLiteralBecomesMap() {
        Object result = evaluate("({a: 1, b: 'x'})");
        assertThat(result).isInstanceOf(Map.class);
        assertThat(asMap(result)).containsExactly(entry("a", 1), entry("b", "x"));
    }

    @Test
    void arrayBecomesList() {
        Object result = evaluate("[1, 'two', true]");
        assertThat(result).isInstanceOf(List.class);
        assertThat(asList(result)).containsExactly(1, "two", true);
    }

    @Test
    void nestedStructuresAreCopiedRecursively() {
        Object result = evaluate("({items: [{k: 'v'}, [1, 2]], n: null})");
        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = asMap(result);
        assertThat(map).containsKeys("items", "n");
        assertThat(map.get("n")).isNull();
        List<Object> items = asList(map.get("items"));
        assertThat(items).hasSize(2);
        assertThat(asMap(items.get(0))).containsExactly(entry("k", "v"));
        assertThat(asList(items.get(1))).containsExactly(1, 2);
    }

    @Test
    void jsMapSetAndDateAreConverted() {
        assertThat(asMap(evaluate("new Map([['k', 1], ['j', 2]])"))).containsExactly(entry("k", 1), entry("j", 2));
        assertThat(asSet(evaluate("new Set([1, 2, 2])"))).containsExactly(1, 2);
        assertThat(evaluate("new Date(0)")).isEqualTo(Instant.EPOCH);
    }

    @Test
    void primitivesAndHostObjectsAreUnchanged() {
        List<String> body = List.of("h");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        assertThat(language.createExpression("body").evaluate(exchange, Object.class)).isSameAs(body);
        assertThat(evaluate("2 + 3")).isEqualTo(5);
        assertThat(evaluate("1.5")).isEqualTo(1.5d);
        assertThat(evaluate("'str'")).isEqualTo("str");
        assertThat(evaluate("true")).isEqualTo(true);
        assertThat(evaluate("undefined")).isNull();
    }

    @Test
    void scriptingLanguageEntryPointMaterializesToo() {
        Map<String, Object> bindings = Map.of("n", 2);
        Map<String, Object> map = asMap(language.evaluate("({n: n, list: [n, n * 2]})", bindings, Map.class));
        assertThat(map).containsKeys("n", "list");
        assertThat(map.get("n")).isEqualTo(2);
        assertThat(asList(map.get("list"))).containsExactly(2, 4);
        assertThat(asList(language.evaluate("[1, 2, 3]", bindings, List.class))).containsExactly(1, 2, 3);
        assertThat(language.evaluate("n + 1", bindings, String.class)).isEqualTo("3");
    }

    private Object evaluate(String script) {
        return language.createExpression(script).evaluate(new DefaultExchange(context), Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> asSet(Object value) {
        assertThat(value).isInstanceOf(Set.class);
        return (Set<Object>) value;
    }
}
