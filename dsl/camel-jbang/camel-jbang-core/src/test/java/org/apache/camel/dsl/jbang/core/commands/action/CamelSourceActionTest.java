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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CamelSourceActionTest {

    @Test
    void testNullReturnsNull() {
        assertNull(CamelSourceAction.extractSourceName(null));
    }

    @Test
    void testPlainFilenameUnchanged() {
        assertEquals("MyRoute.yaml", CamelSourceAction.extractSourceName("MyRoute.yaml"));
    }

    @Test
    void testSchemeAndPathStripsSchemeAndDirectory() {
        // "file:/some/path/MyRoute.yaml" -> strip scheme -> "/some/path/MyRoute.yaml" -> strip path -> "MyRoute.yaml"
        assertEquals("MyRoute.yaml", CamelSourceAction.extractSourceName("file:/some/path/MyRoute.yaml"));
    }

    @Test
    void testClasspathSchemeStripsScheme() {
        assertEquals("routes.xml", CamelSourceAction.extractSourceName("classpath:routes.xml"));
    }

    @Test
    void testSingleColonTreatedAsScheme() {
        // Only one colon: treated as scheme separator, not line number.
        // "MyRoute.java:42" -> substring after ':' = "42" -> stripPath("42") = "42"
        assertEquals("42", CamelSourceAction.extractSourceName("MyRoute.java:42"));
    }

    @Test
    void testSchemeWithLineNumberStripsSchemeAndLineNumber() {
        // Two colons: stripSourceLocationLineNumber removes ":10" -> "file:/path/MyRoute.yaml"
        // then strip scheme and path -> "MyRoute.yaml"
        assertEquals("MyRoute.yaml", CamelSourceAction.extractSourceName("file:/path/MyRoute.yaml:10"));
    }
}
