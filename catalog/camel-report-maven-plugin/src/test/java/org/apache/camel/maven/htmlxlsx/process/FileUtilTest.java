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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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

        assertThat(result).isNotNull();
    }

    @Test
    public void testGetLastElementOfPath() {

        Path path = Paths.get(temporaryDirectory.getPath(), FOOBAR);

        String result = fileUtil.getLastElementOfPath(path.toString());

        assertThat(result).isEqualTo(FOOBAR);
    }

    @Test
    public void testReadFile() throws IOException {

        String content = "x y z";
        Path path = Paths.get(temporaryDirectory.getPath(), FOOBAR);
        File letters = path.toFile();
        Files.write(letters.toPath(), Collections.singletonList(content));

        String result = fileUtil.readFile(letters.getPath());

        assertAll(
                () -> assertThat(Files.exists(letters.toPath())).isTrue(),
                () -> assertThat(result).isNotBlank(),
                () -> assertThat(result.startsWith("x")).isTrue(),
                () -> assertThat(result.endsWith("z\n")).isTrue());
    }

    @Test
    public void testWrite() throws IOException {

        Path path = Paths.get(temporaryDirectory.getPath());

        String content = "x y z";

        String outputPathStr = fileUtil.write(content, FOOBAR, path.toFile());
        Path outputPath = Paths.get((outputPathStr));

        String result = Files.readString(outputPath);

        assertAll(
                () -> assertThat(Files.exists(outputPath)).isTrue(),
                () -> assertThat(result).isNotBlank(),
                () -> assertThat(result.startsWith("x")).isTrue(),
                () -> assertThat(result.endsWith("z")).isTrue());
    }

    @Test
    public void testOutputFile() {

        Path path = Paths.get(temporaryDirectory.getPath());

        Path result = fileUtil.outputFile(FOOBAR, path.toString());

        assertAll(
                () -> assertThat(result.startsWith(path.toString())).isTrue(),
                () -> assertThat(result.endsWith(FOOBAR_HTML)).isTrue());
    }

    @Test
    public void testRemoveFileExtension() {

        String result = fileUtil.removeFileExtension(FOOBAR_HTML);
        assertThat(FOOBAR).isEqualTo(result);

        result = fileUtil.removeFileExtension(null);
        assertThat(result).isNull();

        result = fileUtil.removeFileExtension("");
        assertThat(result).isBlank();
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
                () -> assertThat(result.size()).isEqualTo(2),
                () -> assertThat(result).contains(letters.getPath()),
                () -> assertThat(result).contains(letters2.getPath()));
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

        assertThat(result.isEmpty());
    }

    @Test
    public void testFilesInDirectoryBadDirectory() throws IOException {

        Set<String> result = fileUtil.filesInDirectory(new File(FOOBAR));

        assertThat(result.isEmpty());
    }
}
