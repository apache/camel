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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LauncherHelperTest {

    @Test
    void normalizeJarPathNull() {
        assertNull(LauncherHelper.normalizeJarPath(null));
    }

    @Test
    void normalizeJarPathBlank() {
        assertEquals("", LauncherHelper.normalizeJarPath(""));
    }

    @Test
    void normalizeJarPathPlainJar() {
        assertEquals("/path/to/app.jar", LauncherHelper.normalizeJarPath("/path/to/app.jar"));
    }

    @Test
    void normalizeJarPathFileUrl() {
        assertEquals("/path/to/app.jar", LauncherHelper.normalizeJarPath("file:/path/to/app.jar"));
    }

    @Test
    void normalizeJarPathJarUrl() {
        assertEquals("/path/to/app.jar",
                LauncherHelper.normalizeJarPath("jar:file:/path/to/app.jar!/BOOT-INF/classes"));
    }

    @Test
    void normalizeJarPathNestedUrl() {
        assertEquals("/path/to/app.jar",
                LauncherHelper.normalizeJarPath("nested:/path/to/app.jar!/BOOT-INF/classes"));
    }

    @Test
    void normalizeJarPathWithSpaces() {
        assertEquals("/path/to/my app.jar",
                LauncherHelper.normalizeJarPath("/path/to/my%20app.jar"));
    }

    @Test
    void normalizeJarPathNoJarExtension() {
        // When there is no .jar extension the path should be returned as-is (URL-decoded)
        assertEquals("/path/to/classes", LauncherHelper.normalizeJarPath("/path/to/classes"));
    }

    @Test
    void normalizeJarPathNestedUrlNoJarExtension() {
        // Nested URL where the outer .jar is stripped but no .jar remains should return decoded path
        assertEquals("/path/to/classes",
                LauncherHelper.normalizeJarPath("nested:/path/to/classes!/inner"));
    }
}
