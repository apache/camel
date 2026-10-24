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
package org.apache.camel.language.python;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.test.junit6.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A script returns the Java value of its result, not its Python text, and an error while it runs is an evaluation
 * error, not a syntax error.
 */
class PythonResultTest extends LanguageTestSupport {

    private Object evaluate(String script) {
        Language language = context.resolveLanguage("python");
        return language.createExpression(script).evaluate(exchange, Object.class);
    }

    private ScriptingLanguage scripting() {
        return (ScriptingLanguage) context.resolveLanguage("python");
    }

    @Test
    void pythonValuesAreJavaValues() {
        assertNull(evaluate("None"));
        assertEquals(3, evaluate("1 + 2"));
        assertEquals(1.5, evaluate("3 / 2.0"));
        assertEquals(Boolean.TRUE, evaluate("1 == 1"));
        assertEquals("abc", evaluate("'abc'"));
        assertEquals("abc", evaluate("u'abc'"));
    }

    @Test
    void bodyIsReturnedAsItIs() {
        byte[] data = { 1, 2, 3 };
        exchange.getMessage().setBody(data);

        assertSame(data, evaluate("body"));
    }

    @Test
    void collectionsAreJavaCollections() {
        Object map = evaluate("{'a': 1, 'b': [1, None, {'c': 'd'}]}");
        assertInstanceOf(LinkedHashMap.class, map);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("c", "d");
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(null);
        list.add(nested);
        assertEquals(Map.of("a", 1, "b", list), map);
        assertInstanceOf(LinkedHashMap.class, ((List<?>) ((Map<?, ?>) map).get("b")).get(2));

        assertEquals(List.of(1, "x"), evaluate("(1, 'x')"));
        assertEquals(Set.of(2), evaluate("set([2])"));
    }

    @Test
    void resultIsConvertedToTheRequestedType() {
        assertExpression("1 + 2", "3");
        assertExpression("'5'", 5);
    }

    @Test
    void noneIsAFalsePredicate() {
        assertPredicate("None", false);
        assertPredicate("1 == 2", false);
        assertPredicate("1 == 1", true);
    }

    @Test
    void scriptingReturnsJavaValues() {
        assertNull(scripting().evaluate("None", null, Object.class));
        assertEquals(3, scripting().evaluate("1 + 2", null, Object.class));
        assertEquals(Map.of("a", 1), scripting().evaluate("{'a': 1}", null, Object.class));
    }

    @Test
    void runtimeErrorIsAnEvaluationError() {
        Language language = context.resolveLanguage("python");

        ExpressionEvaluationException e = assertThrows(ExpressionEvaluationException.class,
                () -> language.createExpression("unknown + 1").evaluate(exchange, Object.class));
        assertTrue(e.getCause().toString().contains("NameError"));

        e = assertThrows(ExpressionEvaluationException.class,
                () -> scripting().evaluate("1 / 0", null, Object.class));
        assertTrue(e.getCause().toString().contains("ZeroDivisionError"));
    }

    @Test
    void syntaxErrorIsASyntaxError() {
        Language language = context.resolveLanguage("python");

        assertThrows(ExpressionIllegalSyntaxException.class, () -> language.createExpression("1 +"));
        assertThrows(ExpressionIllegalSyntaxException.class, () -> scripting().evaluate("1 +", null, Object.class));
    }

    @Override
    protected String getLanguageName() {
        return "python";
    }
}
