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
package org.apache.camel.maven.htmlxlsx.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest {

    private static final String BARFOO = "barfoo";

    private static final String FOOBAR = "foobar";

    private static final String FOOBAR_HTML = "foobar.html";

    private final FileUtil fileUtil = new FileUtil();

    @TempDir
    private File temporaryDirectory;

    @Test
    public void testFileUtil() {

        // keep jacoco happy
        FileUtil result = new FileUtil();

        assertNotNull(result);
    }

    @Test
    public void testGetLastElementOfPath() {

        Path path = Paths.get(temporaryDirectory.getPath(), FOOBAR);

        String result = fileUtil.getLastElementOfPath(path.toString());

        assertEquals(FOOBAR, result);
    }

    @Test
    public void testReadFile() throws IOException {

        String content = "x y z";
        Path path = Paths.get(temporaryDirectory.getPath(), FOOBAR);
        File letters = path.toFile();
        Files.write(letters.toPath(), Collections.singletonList(content));

        String result = fileUtil.readFile(letters.getPath());

        assertAll(
                () -> assertTrue(Files.exists(letters.toPath())),
                () -> assertTrue(result.length() > 0),
                () -> assertTrue(result.startsWith("x")),
                () -> assertTrue(result.endsWith("z\n")));
    }

    @Test
    public void testWrite() throws IOException {

        Path path = Paths.get(temporaryDirectory.getPath());

        String content = "x y z";

        String outputPathStr = fileUtil.write(content, FOOBAR, path.toFile());
        Path outputPath = Paths.get((outputPathStr));

        String result = Files.readString(outputPath);

        assertAll(
                () -> assertTrue(Files.exists(outputPath)),
                () -> assertTrue(result.length() > 0),
                () -> assertTrue(result.startsWith("x")),
                () -> assertTrue(result.endsWith("z")));
    }

    @Test
    public void testOutputFile() {

        Path path = Paths.get(temporaryDirectory.getPath());

        Path result = fileUtil.outputFile(FOOBAR, path.toString());

        assertAll(
                () -> assertTrue(result.startsWith(path.toString())),
                () -> assertTrue(result.endsWith(FOOBAR_HTML)));
    }

    @Test
    public void testRemoveFileExtension() {

        String result = fileUtil.removeFileExtension(FOOBAR_HTML);
        assertEquals(FOOBAR, result);

        result = fileUtil.removeFileExtension(null);
        assertNull(result);

        result = fileUtil.removeFileExtension("");
        assertEquals(0, result.length());
    }

    @Test
    public void testFilesInDirectory() throws IOException {

        String content = "x y z";
        Path path = Paths.get(temporaryDirectory.getPath(), FOOBAR);
        File letters = path.toFile();
        Files.write(letters.toPath(), Collections.singletonList(content));

        path = Paths.get(temporaryDirectory.getPath(), FOOBAR_HTML);
        File letters2 = path.toFile();
        Files.write(letters2.toPath(), Collections.singletonList(content));

        Set<String> result = fileUtil.filesInDirectory(temporaryDirectory);

        assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.contains(letters.getPath())),
                () -> assertTrue(result.contains(letters2.getPath())));
    }

    @Test
    public void testFilesInDirectorySubDirectory() throws IOException {

        String content = "x y z";
        Path path = Paths.get(temporaryDirectory.getPath(), BARFOO);
        Files.createDirectory(path);

        path = Paths.get(path.toString(), FOOBAR_HTML);
        File letters = path.toFile();
        Files.write(letters.toPath(), Collections.singletonList(content));

        Set<String> result = fileUtil.filesInDirectory(temporaryDirectory);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilesInDirectoryBadDirectory() throws IOException {

        Set<String> result = fileUtil.filesInDirectory(new File(FOOBAR));

        assertTrue(result.isEmpty());
    }
}
