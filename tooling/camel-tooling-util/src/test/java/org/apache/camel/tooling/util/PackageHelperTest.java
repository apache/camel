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
package org.apache.camel.tooling.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackageHelperTest {

    @Test
    public void testFileToString() throws Exception {
        File file = ResourceUtils.getResourceAsFile("filecontent/a.txt");
        assertEquals("dk19i21)@+#(OR", PackageHelper.loadText(file));
    }

    @Test
    public void testFindJsonFiles() throws Exception {
        Path dir = ResourceUtils.getResourceAsFile("json").toPath();
        List<String> jsonFiles;
        try (Stream<Path> stream = PackageHelper.findJsonFiles(dir)) {
            jsonFiles = stream
                    .map(PackageHelper::asName)
                    .toList();
        }

        assertTrue(jsonFiles.contains("a"), "Files a.json must be found");
        assertTrue(jsonFiles.contains("b"), "Files b.json must be found");
        assertFalse(jsonFiles.contains("c"), "File c.txt must not be found");
    }

    @Test
    public void testGetSchemaKind() throws Exception {
        File file = ResourceUtils.getResourceAsFile("json/aop.json");
        String json = PackageHelper.loadText(file);
        assertEquals("model", PackageHelper.getSchemaKind(json));
    }
}
