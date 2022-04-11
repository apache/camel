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
package org.apache.camel.language.groovy;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroovyScriptingLanguageTest extends CamelTestSupport {

    @Test
    public void testScripting() {
        Language lan = context.resolveLanguage("groovy");
        Assertions.assertTrue(lan instanceof ScriptingLanguage);
        ScriptingLanguage slan = (ScriptingLanguage) lan;

        int num = slan.evaluate("2 * 3", null, int.class);
        Assertions.assertEquals(6, num);

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("context", context());
        String id = slan.evaluate("context.name", bindings, String.class);
        Assertions.assertEquals(context.getName(), id);
    }

    @Test
    public void testExternalScripting() {
        Language lan = context.resolveLanguage("groovy");
        Assertions.assertTrue(lan instanceof ScriptingLanguage);
        ScriptingLanguage slan = (ScriptingLanguage) lan;

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("body", 3);
        String text = slan.evaluate("resource:classpath:mygroovy.groovy", bindings, String.class);
        Assertions.assertEquals("The result is 6", text);
    }

}
