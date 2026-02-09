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

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for VariablesDevConsole with variables configured.
 */
public class VariablesDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected void doPostSetup() {
        context.setVariable("testVar", "testValue");
        context.setVariable("anotherVar", 42);
    }

    @Test
    public void testVariablesConsole() {
        DevConsole console = assertConsoleExists("variables", "camel");

        String textOut = callText(console);
        assertTrue(textOut.contains("testVar"));
        assertTrue(textOut.contains("testValue"));
        assertTrue(textOut.contains("anotherVar"));
        assertTrue(textOut.contains("42"));

        JsonObject jsonOut = callJson(console);
        assertFalse(jsonOut.isEmpty());
    }
}
