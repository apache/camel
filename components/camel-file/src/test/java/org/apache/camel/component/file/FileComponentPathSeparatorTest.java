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
package org.apache.camel.component.file;

import java.util.HashMap;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileComponentPathSeparatorTest {

    @Test
    public void shouldNormalizeBackslashesInAbsolutePath() throws Exception {
        FileComponent component = new FileComponent();
        FileEndpoint endpoint = (FileEndpoint) component.buildFileEndpoint(
                "file:C:\\Users\\test\\target", "C:\\Users\\test\\target", new HashMap<>());

        String directory = endpoint.getConfiguration().getDirectory();
        String filePath = endpoint.getFile().getPath();

        String expected = FileUtil.normalizePath("C:/Users/test/target");
        assertEquals(expected, directory);
        assertEquals(expected, filePath);
    }

    @Test
    public void shouldNormalizeBackslashesInRelativePath() throws Exception {
        FileComponent component = new FileComponent();
        FileEndpoint endpoint = (FileEndpoint) component.buildFileEndpoint(
                "file:target\\subdir", "target\\subdir", new HashMap<>());

        String directory = endpoint.getConfiguration().getDirectory();
        String filePath = endpoint.getFile().getPath();

        String expected = FileUtil.normalizePath("target/subdir");
        assertEquals(expected, directory);
        assertEquals(expected, filePath);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void shouldPreserveForwardSlashPaths() throws Exception {
        FileComponent component = new FileComponent();
        FileEndpoint endpoint = (FileEndpoint) component.buildFileEndpoint(
                "file:/home/user/target", "/home/user/target", new HashMap<>());

        String directory = endpoint.getConfiguration().getDirectory();
        String filePath = endpoint.getFile().getPath();

        String expected = FileUtil.normalizePath("/home/user/target");
        assertEquals(expected, directory);
        assertEquals(expected, filePath);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void shouldNormalizePosixBackslashOnNonWindows() throws Exception {
        FileComponent component = new FileComponent();
        FileEndpoint endpoint = (FileEndpoint) component.buildFileEndpoint(
                "file:/tmp/a\\b", "/tmp/a\\b", new HashMap<>());

        String directory = endpoint.getConfiguration().getDirectory();
        String filePath = endpoint.getFile().getPath();

        assertEquals("/tmp/a/b", directory);
        assertEquals("/tmp/a/b", filePath);
    }
}
