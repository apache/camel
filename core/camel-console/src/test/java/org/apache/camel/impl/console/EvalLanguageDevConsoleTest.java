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
package org.apache.camel.impl.console;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EvalLanguageDevConsoleTest extends AbstractDevConsoleTest {

    @Test
    public void testEvalWithBody() {
        DevConsole con = assertConsoleExists("eval-language", "camel");

        JsonObject out = callJson(con, Map.of(
                "language", "simple",
                "template", "${body}",
                "body", "Hello World"));

        Assertions.assertEquals("success", out.getString("status"));
        Assertions.assertEquals("Hello World", out.getString("result"));
    }

    @Test
    public void testEvalWithHeaders() {
        DevConsole con = assertConsoleExists("eval-language", "camel");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("myKey", "myValue");

        JsonObject out = callJson(con, Map.of(
                "language", "simple",
                "template", "${header.myKey}",
                "body", "",
                "headers", headers));

        Assertions.assertEquals("success", out.getString("status"));
        Assertions.assertEquals("myValue", out.getString("result"));
    }

    @Test
    public void testEvalWithVariables() {
        DevConsole con = assertConsoleExists("eval-language", "camel");

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("foo", "bar");

        JsonObject out = callJson(con, Map.of(
                "language", "simple",
                "template", "${variable.foo}",
                "body", "",
                "variables", variables));

        Assertions.assertEquals("success", out.getString("status"));
        Assertions.assertEquals("bar", out.getString("result"));
    }

    @Test
    public void testEvalPredicate() {
        DevConsole con = assertConsoleExists("eval-language", "camel");

        JsonObject out = callJson(con, Map.of(
                "language", "simple",
                "template", "${body} == 'Hello'",
                "body", "Hello",
                "predicate", true));

        Assertions.assertEquals("success", out.getString("status"));
        Assertions.assertEquals("true", out.getString("result"));
    }

    @Test
    public void testEvalWithBodyAndVariables() {
        DevConsole con = assertConsoleExists("eval-language", "camel");

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("greeting", "Hi");

        JsonObject out = callJson(con, Map.of(
                "language", "simple",
                "template", "${variable.greeting} ${body}",
                "body", "World",
                "variables", variables));

        Assertions.assertEquals("success", out.getString("status"));
        Assertions.assertEquals("Hi World", out.getString("result"));
    }
}
