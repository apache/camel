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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagramScriptsTest {

    @Test
    void shouldLoadConnectScript() {
        DiagramScripts scripts = new DiagramScripts();
        String content = scripts.load("diagram-connect.js");
        assertNotNull(content);
        assertFalse(content.isBlank());
    }

    @Test
    void shouldLoadMainScript() {
        DiagramScripts scripts = new DiagramScripts();
        String content = scripts.load("diagram-scripts.js");
        assertNotNull(content);
        assertFalse(content.isBlank());
    }

    @Test
    void shouldCacheScripts() {
        DiagramScripts scripts = new DiagramScripts();
        String first = scripts.load("diagram-connect.js");
        String second = scripts.load("diagram-connect.js");
        assertSame(first, second);
    }

    @Test
    void shouldThrowForUnknownScript() {
        DiagramScripts scripts = new DiagramScripts();
        assertThrows(IllegalStateException.class, () -> scripts.load("nonexistent.js"));
    }
}
