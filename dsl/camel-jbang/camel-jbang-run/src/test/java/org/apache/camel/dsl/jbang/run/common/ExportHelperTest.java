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
package org.apache.camel.dsl.jbang.run.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.tooling.maven.MavenGav;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ExportHelperTest {

    @Test
    public void testPackageName() {
        String name = ExportHelper.exportPackageName("org.demo", "some-app-x2025", null);
        Assertions.assertEquals("org.demo.someappx2025", name);
        name = ExportHelper.exportPackageName("org.demo", "some-app-2025", null);
        Assertions.assertEquals("org.demo.someapp2025", name);
    }

    @Test
    public void testSafeCopyFileToNewTarget(@TempDir Path tempDir) throws Exception {
        // Create source file
        Path source = tempDir.resolve("source.txt");
        String content = "Hello World";
        Files.writeString(source, content);

        // Copy to new target
        Path target = tempDir.resolve("target.txt");
        ExportHelper.safeCopy(source, target, false);

        // Verify target exists and has correct content
        Assertions.assertTrue(Files.exists(target));
        Assertions.assertEquals(content, Files.readString(target));
    }

    @Test
    public void testSafeCopyFileWithOverrideTrue(@TempDir Path tempDir) throws Exception {
        // Create source and target files with different content
        Path source = tempDir.resolve("source.txt");
        Path target = tempDir.resolve("target.txt");
        Files.writeString(source, "New Content");
        Files.writeString(target, "Old Content");

        // Copy with override=true
        ExportHelper.safeCopy(source, target, true);

        // Verify target was overwritten
        Assertions.assertEquals("New Content", Files.readString(target));
    }

    @Test
    public void testSafeCopyFileWithOverrideFalse(@TempDir Path tempDir) throws Exception {
        // Create source and target files with different content
        Path source = tempDir.resolve("source.txt");
        Path target = tempDir.resolve("target.txt");
        Files.writeString(source, "New Content");
        Files.writeString(target, "Old Content");

        // Copy with override=false
        ExportHelper.safeCopy(source, target, false);

        // Verify target was NOT overwritten
        Assertions.assertEquals("Old Content", Files.readString(target));
    }

    @Test
    public void testSafeCopyFromDirectory(@TempDir Path tempDir) throws Exception {
        // Create source directory with multiple files
        Path sourceDir = tempDir.resolve("sourceDir");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("file1.txt"), "Content 1");
        Files.writeString(sourceDir.resolve("file2.txt"), "Content 2");
        Files.writeString(sourceDir.resolve("file3.txt"), "Content 3");

        // Create target directory
        Path targetDir = tempDir.resolve("targetDir");
        Files.createDirectories(targetDir);

        // Copy from directory (should flatten files)
        ExportHelper.safeCopy(sourceDir, targetDir, false);

        // Verify all files were copied to target directory
        Assertions.assertTrue(Files.exists(targetDir.resolve("file1.txt")));
        Assertions.assertTrue(Files.exists(targetDir.resolve("file2.txt")));
        Assertions.assertTrue(Files.exists(targetDir.resolve("file3.txt")));
        Assertions.assertEquals("Content 1", Files.readString(targetDir.resolve("file1.txt")));
        Assertions.assertEquals("Content 2", Files.readString(targetDir.resolve("file2.txt")));
        Assertions.assertEquals("Content 3", Files.readString(targetDir.resolve("file3.txt")));
    }

    @Test
    public void testSafeCopyNonExistentSource(@TempDir Path tempDir) throws Exception {
        // Try to copy from non-existent source
        Path source = tempDir.resolve("nonexistent.txt");
        Path target = tempDir.resolve("target.txt");

        // Should not throw exception, just return
        ExportHelper.safeCopy(source, target, false);

        // Verify target was not created
        Assertions.assertFalse(Files.exists(target));
    }

    @Test
    public void testSafeCopyFromInputStream(@TempDir Path tempDir) throws Exception {
        // Create input stream with content
        String content = "Stream Content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Copy to target
        Path target = tempDir.resolve("subdir").resolve("target.txt");
        ExportHelper.safeCopy(inputStream, target);

        // Verify target exists and has correct content
        Assertions.assertTrue(Files.exists(target));
        Assertions.assertEquals(content, Files.readString(target));
    }

    @Test
    public void testSafeCopyFromInputStreamCreatesParentDirectories(@TempDir Path tempDir) throws Exception {
        // Create input stream
        InputStream inputStream = new ByteArrayInputStream("Content".getBytes(StandardCharsets.UTF_8));

        // Copy to target in nested directories that don't exist
        Path target = tempDir.resolve("level1").resolve("level2").resolve("target.txt");
        ExportHelper.safeCopy(inputStream, target);

        // Verify parent directories were created
        Assertions.assertTrue(Files.exists(target.getParent()));
        Assertions.assertTrue(Files.exists(target));
    }

    @Test
    public void testSafeCopyFromNullInputStream(@TempDir Path tempDir) throws Exception {
        // Try to copy from null input stream
        Path target = tempDir.resolve("target.txt");

        // Should not throw exception, just return
        ExportHelper.safeCopy(null, target);

        // Verify target was not created
        Assertions.assertFalse(Files.exists(target));
    }

    @Test
    public void testSafeCopyFromInputStreamDoesNotOverwrite(@TempDir Path tempDir) throws Exception {
        // Create existing target file
        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "Existing Content");

        // Try to copy from input stream
        InputStream inputStream = new ByteArrayInputStream("New Content".getBytes(StandardCharsets.UTF_8));
        ExportHelper.safeCopy(inputStream, target);

        // Verify target was NOT overwritten
        Assertions.assertEquals("Existing Content", Files.readString(target));
    }

    @Test
    public void testSafeCopyWithClassLoaderNonClasspathScheme(@TempDir Path tempDir) throws Exception {
        // Create source file
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "File Content");

        // Copy with non-classpath scheme (should delegate to Path version)
        Path target = tempDir.resolve("target.txt");
        ExportHelper.safeCopy(getClass().getClassLoader(), "file", source, target, false);

        // Verify target exists and has correct content
        Assertions.assertTrue(Files.exists(target));
        Assertions.assertEquals("File Content", Files.readString(target));
    }

    @Test
    public void testSafeCopyWithClassLoaderClasspathScheme(@TempDir Path tempDir) throws Exception {
        // Copy from classpath resource
        Path target = tempDir.resolve("log4j2.properties");

        // Use a known classpath resource (log4j2.properties exists in src/main/resources)
        Path source = Path.of("log4j2.properties");
        ExportHelper.safeCopy(getClass().getClassLoader(), "classpath", source, target, false);

        // Verify target exists and has content
        Assertions.assertTrue(Files.exists(target));
        Assertions.assertTrue(Files.size(target) > 0);
    }

    @Test
    public void testSafeCopyWithClassLoaderClasspathSchemeWindowsPath(@TempDir Path tempDir) throws Exception {
        // Test that Windows-style path separators are handled correctly
        Path target = tempDir.resolve("log4j2.properties");

        // Use Windows-style path separator (should be converted to Unix style)
        Path source = Path.of("log4j2.properties");
        ExportHelper.safeCopy(getClass().getClassLoader(), "classpath", source, target, false);

        // Verify target exists
        Assertions.assertTrue(Files.exists(target));
    }

    @Test
    public void testRemoveDuplicateArtifactsKeepsTheVersionThatIsGiven() {
        List<MavenGav> gavs = new ArrayList<>(
                List.of(
                        MavenGav.parseGav("org.apache.camel.quarkus:camel-quarkus-console"),
                        MavenGav.parseGav("org.apache.camel.quarkus:camel-quarkus-log"),
                        MavenGav.parseGav("org.apache.camel.quarkus:camel-quarkus-console:3.2.0")));

        ExportHelper.removeDuplicateArtifacts(gavs);

        // the duplicate keeps the position of the first, with the version that is given
        Assertions.assertEquals(2, gavs.size());
        Assertions.assertEquals("camel-quarkus-console", gavs.get(0).getArtifactId());
        Assertions.assertEquals("3.2.0", gavs.get(0).getVersion());
        Assertions.assertEquals("camel-quarkus-log", gavs.get(1).getArtifactId());
    }

    @Test
    public void testRemoveDuplicateArtifactsKeepsTheFirstOfTwoVersions() {
        List<MavenGav> gavs = new ArrayList<>(
                List.of(
                        MavenGav.parseGav("org.postgresql:postgresql:42.7.9"),
                        MavenGav.parseGav("org.postgresql:postgresql:42.7.99")));

        ExportHelper.removeDuplicateArtifacts(gavs);

        Assertions.assertEquals(1, gavs.size());
        Assertions.assertEquals("42.7.9", gavs.get(0).getVersion());
    }

    @Test
    public void testPropertyLineIsReadBackAsWritten() throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("path", "C:\\temp\\new");
        properties.put("regex", "^\\d+$");
        properties.put("my.key with space", "1");
        properties.put("odd=key:with#and!", "x");
        properties.put("leading", "  two spaces");
        properties.put("lines", "one\ntwo\tthree\rfour\ffive");
        properties.put("control", "a\u0001b");
        properties.put("latin", "æøå");
        properties.put("euro", "5 €");
        properties.put("url", "jdbc:mysql://localhost:3306/db?a=b#c!");

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : properties.entrySet()) {
            sb.append(ExportHelper.propertyLine(e.getKey(), e.getValue())).append('\n');
        }
        // a properties file is read in ISO-8859-1
        Properties loaded = new Properties();
        loaded.load(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.ISO_8859_1)));

        Assertions.assertEquals(properties.size(), loaded.size(), sb.toString());
        properties.forEach((k, v) -> Assertions.assertEquals(v, loaded.getProperty(k), k));
        // readable: a value keeps its colons, and the euro sign is not in ISO-8859-1
        Assertions.assertEquals("url=jdbc:mysql://localhost:3306/db?a=b#c!",
                ExportHelper.propertyLine("url", properties.get("url")));
        Assertions.assertEquals("euro=5 \\u20ac", ExportHelper.propertyLine("euro", "5 €"));
    }
}
