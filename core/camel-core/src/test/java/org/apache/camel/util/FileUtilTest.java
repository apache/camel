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
package org.apache.camel.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.camel.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest extends TestSupport {

    @Test
    public void testNormalizePath() {
        if (FileUtil.isWindows()) {
            assertEquals("foo\\bar", FileUtil.normalizePath("foo/bar"));
            assertEquals("foo\\bar\\baz", FileUtil.normalizePath("foo/bar\\baz"));
            assertEquals("movefile\\sub\\sub2\\.done\\goodday.txt",
                    FileUtil.normalizePath("movefile/sub/sub2\\.done\\goodday.txt"));
        } else {
            assertEquals("foo/bar", FileUtil.normalizePath("foo/bar"));
            assertEquals("foo/bar/baz", FileUtil.normalizePath("foo/bar\\baz"));
            assertEquals("movefile/sub/sub2/.done/goodday.txt",
                    FileUtil.normalizePath("movefile/sub/sub2\\.done\\goodday.txt"));
        }
    }

    @Test
    public void testStripLeadingSeparator() {
        assertNull(FileUtil.stripLeadingSeparator(null));
        assertEquals("foo", FileUtil.stripLeadingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("foo/bar"));
        assertEquals("foo/", FileUtil.stripLeadingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("/foo/bar"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("//foo/bar"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("///foo/bar"));
    }

    @Test
    public void testHasLeadingSeparator() {
        assertFalse(FileUtil.hasLeadingSeparator(null));
        assertFalse(FileUtil.hasLeadingSeparator("foo"));
        assertFalse(FileUtil.hasLeadingSeparator("foo/bar"));
        assertFalse(FileUtil.hasLeadingSeparator("foo/"));
        assertTrue(FileUtil.hasLeadingSeparator("/foo/bar"));
        assertTrue(FileUtil.hasLeadingSeparator("//foo/bar"));
        assertTrue(FileUtil.hasLeadingSeparator("///foo/bar"));
    }

    @Test
    public void testStripFirstLeadingSeparator() {
        assertNull(FileUtil.stripFirstLeadingSeparator(null));
        assertEquals("foo", FileUtil.stripFirstLeadingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripFirstLeadingSeparator("foo/bar"));
        assertEquals("foo/", FileUtil.stripFirstLeadingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripFirstLeadingSeparator("/foo/bar"));
        assertEquals("/foo/bar", FileUtil.stripFirstLeadingSeparator("//foo/bar"));
        assertEquals("//foo/bar", FileUtil.stripFirstLeadingSeparator("///foo/bar"));
    }

    @Test
    public void testStripTrailingSeparator() {
        assertNull(FileUtil.stripTrailingSeparator(null));
        assertEquals("foo", FileUtil.stripTrailingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripTrailingSeparator("foo/bar"));
        assertEquals("foo", FileUtil.stripTrailingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripTrailingSeparator("foo/bar/"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar/"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar//"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar///"));

        assertEquals("/foo", FileUtil.stripTrailingSeparator("/foo"));
        assertEquals("/foo", FileUtil.stripTrailingSeparator("/foo/"));
        assertEquals("/", FileUtil.stripTrailingSeparator("/"));
        assertEquals("//", FileUtil.stripTrailingSeparator("//"));
    }

    @Test
    public void testStripPath() {
        assertNull(FileUtil.stripPath(null));
        assertEquals("", FileUtil.stripPath("/"));
        assertEquals("foo.xml", FileUtil.stripPath("/foo.xml"));
        assertEquals("foo", FileUtil.stripPath("foo"));
        assertEquals("bar", FileUtil.stripPath("foo/bar"));
        assertEquals("bar", FileUtil.stripPath("/foo/bar"));
    }

    @Test
    public void testStripPathWithMixedSeparators() {
        assertNull(FileUtil.stripPath(null));
        assertEquals("", FileUtil.stripPath("/"));
        assertEquals("foo.xml", FileUtil.stripPath("/foo.xml"));
        assertEquals("foo", FileUtil.stripPath("foo"));
        assertEquals("baz", FileUtil.stripPath("foo/bar\\baz"));
        assertEquals("bar", FileUtil.stripPath("\\foo\\bar"));
        assertEquals("baz", FileUtil.stripPath("/foo\\bar/baz"));
    }

    @Test
    public void testStripExt() {
        assertNull(FileUtil.stripExt(null));
        assertEquals("foo", FileUtil.stripExt("foo"));
        assertEquals("foo", FileUtil.stripExt("foo.xml"));
        assertEquals("/foo/bar", FileUtil.stripExt("/foo/bar.xml"));
    }

    @Test
    public void testOnlyExt() {
        assertNull(FileUtil.onlyExt(null));
        assertNull(FileUtil.onlyExt("foo"));
        assertEquals("xml", FileUtil.onlyExt("foo.xml"));
        assertEquals("xml", FileUtil.onlyExt("/foo/bar.xml"));
        assertEquals("tar.gz", FileUtil.onlyExt("/foo/bigfile.tar.gz"));
        assertEquals("tar.gz", FileUtil.onlyExt("/foo.bar/bigfile.tar.gz"));
    }

    @Test
    public void testOnlyPath() {
        assertNull(FileUtil.onlyPath(null));
        assertNull(FileUtil.onlyPath("foo"));
        assertNull(FileUtil.onlyPath("foo.xml"));
        assertEquals("foo", FileUtil.onlyPath("foo/bar.xml"));
        assertEquals("/foo", FileUtil.onlyPath("/foo/bar.xml"));
        assertEquals("/foo/bar", FileUtil.onlyPath("/foo/bar/baz.xml"));
        assertEquals("/", FileUtil.onlyPath("/foo.xml"));
        assertEquals("/bar", FileUtil.onlyPath("/bar/foo.xml"));
    }

    @Test
    public void testOnlyPathWithMixedSeparators() {
        assertNull(FileUtil.onlyPath(null));
        assertNull(FileUtil.onlyPath("foo"));
        assertNull(FileUtil.onlyPath("foo.xml"));
        assertEquals("foo", FileUtil.onlyPath("foo/bar.xml"));
        assertEquals("/foo", FileUtil.onlyPath("/foo\\bar.xml"));
        assertEquals("\\foo\\bar", FileUtil.onlyPath("\\foo\\bar/baz.xml"));
        assertEquals("\\", FileUtil.onlyPath("\\foo.xml"));
        assertEquals("/bar", FileUtil.onlyPath("/bar\\foo.xml"));
    }

    @Test
    public void testOnlyName() {
        assertNull(FileUtil.onlyName(null));
        assertEquals("foo", FileUtil.onlyName("foo"));
        assertEquals("foo", FileUtil.onlyName("foo.xml"));
        assertEquals("bar", FileUtil.onlyName("foo/bar.xml"));
        assertEquals("bar", FileUtil.onlyName("/foo/bar.xml"));
        assertEquals("baz", FileUtil.onlyName("/foo/bar/baz.xml"));
        assertEquals("foo", FileUtil.onlyName("/foo.xml"));
        assertEquals("foo", FileUtil.onlyName("/bar/foo.xml"));
    }

    @Test
    public void testCompactPath() {
        assertNull(FileUtil.compactPath(null));
        if (FileUtil.isWindows()) {
            assertEquals("..\\foo", FileUtil.compactPath("..\\foo"));
            assertEquals("..\\..\\foo", FileUtil.compactPath("..\\..\\foo"));
            assertEquals("..\\..\\foo\\bar", FileUtil.compactPath("..\\..\\foo\\bar"));
            assertEquals("..\\..\\foo", FileUtil.compactPath("..\\..\\foo\\bar\\.."));
            assertEquals("foo", FileUtil.compactPath("foo"));
            assertEquals("bar", FileUtil.compactPath("foo\\..\\bar"));
            assertEquals("bar\\baz", FileUtil.compactPath("foo\\..\\bar\\baz"));
            assertEquals("foo\\baz", FileUtil.compactPath("foo\\bar\\..\\baz"));
            assertEquals("baz", FileUtil.compactPath("foo\\bar\\..\\..\\baz"));
            assertEquals("..\\baz", FileUtil.compactPath("foo\\bar\\..\\..\\..\\baz"));
            assertEquals("..\\foo\\bar", FileUtil.compactPath("..\\foo\\bar"));
            assertEquals("foo\\bar\\baz", FileUtil.compactPath("foo\\bar\\.\\baz"));
            assertEquals("foo\\bar\\baz", FileUtil.compactPath("foo\\bar\\\\baz"));
            assertEquals("\\foo\\bar\\baz", FileUtil.compactPath("\\foo\\bar\\baz"));
            // Test that multiple back-slashes at the beginning are preserved,
            // this is necessary for network UNC paths.
            assertEquals("\\\\foo\\bar\\baz", FileUtil.compactPath("\\\\foo\\bar\\baz"));
            assertEquals("\\", FileUtil.compactPath("\\"));
            assertEquals("\\", FileUtil.compactPath("/"));
            assertEquals("/", FileUtil.compactPath("\\", '/'));
            assertEquals("/", FileUtil.compactPath("/", '/'));
        } else {
            assertEquals("../foo", FileUtil.compactPath("../foo"));
            assertEquals("../../foo", FileUtil.compactPath("../../foo"));
            assertEquals("../../foo/bar", FileUtil.compactPath("../../foo/bar"));
            assertEquals("../../foo", FileUtil.compactPath("../../foo/bar/.."));
            assertEquals("foo", FileUtil.compactPath("foo"));
            assertEquals("bar", FileUtil.compactPath("foo/../bar"));
            assertEquals("bar/baz", FileUtil.compactPath("foo/../bar/baz"));
            assertEquals("foo/baz", FileUtil.compactPath("foo/bar/../baz"));
            assertEquals("baz", FileUtil.compactPath("foo/bar/../../baz"));
            assertEquals("../baz", FileUtil.compactPath("foo/bar/../../../baz"));
            assertEquals("../foo/bar", FileUtil.compactPath("../foo/bar"));
            assertEquals("foo/bar/baz", FileUtil.compactPath("foo/bar/./baz"));
            assertEquals("foo/bar/baz", FileUtil.compactPath("foo/bar//baz"));
            assertEquals("/foo/bar/baz", FileUtil.compactPath("/foo/bar/baz"));
            // Do not preserve multiple slashes at the beginning if not on
            // Windows.
            assertEquals("/foo/bar/baz", FileUtil.compactPath("//foo/bar/baz"));
            assertEquals("/", FileUtil.compactPath("/"));
            assertEquals("/", FileUtil.compactPath("\\"));
            assertEquals("/", FileUtil.compactPath("/", '/'));
            assertEquals("/", FileUtil.compactPath("\\", '/'));
        }
    }

    @Test
    public void testCompactWindowsStylePath() {
        String path = "E:\\workspace\\foo\\bar\\some-thing\\.\\target\\processes\\2";
        String expected = "E:\\workspace\\foo\\bar\\some-thing\\target\\processes\\2";
        assertEquals(expected, FileUtil.compactPath(path, '\\'));
    }

    @Test
    public void testCompactPathSeparator() {
        assertNull(FileUtil.compactPath(null, '\''));
        assertEquals("..\\foo", FileUtil.compactPath("..\\foo", '\\'));
        assertEquals("../foo", FileUtil.compactPath("../foo", '/'));

        assertEquals("../foo/bar", FileUtil.compactPath("../foo\\bar", '/'));
        assertEquals("..\\foo\\bar", FileUtil.compactPath("../foo\\bar", '\\'));
    }

    @Test
    public void testDefaultTempFileSuffixAndPrefix() throws Exception {
        File tmp = FileUtil.createTempFile("tmp-", ".tmp", testDirectory("tmp").toFile());
        assertNotNull(tmp);
        assertTrue(tmp.isFile(), "Should be a file");
    }

    @Test
    public void testDefaultTempFile() throws Exception {
        File tmp = FileUtil.createTempFile(null, null, testDirectory("tmp").toFile());
        assertNotNull(tmp);
        assertTrue(tmp.isFile(), "Should be a file");
    }

    @Test
    public void testDefaultTempFileParent() throws Exception {
        File tmp = FileUtil.createTempFile(null, null, testDirectory().toFile());
        assertNotNull(tmp);
        assertTrue(tmp.isFile(), "Should be a file");
    }

    @Test
    public void testCreateNewFile() throws Exception {
        File file = testFile("foo.txt").toFile();
        if (file.exists()) {
            FileUtil.deleteFile(file);
        }

        assertFalse(file.exists(), "File should not exist " + file);
        assertTrue(FileUtil.createNewFile(file), "A new file should be created " + file);
    }

    @Test
    public void testRenameUsingDelete() throws Exception {
        File file = testFile("foo.txt").toFile();
        if (!file.exists()) {
            FileUtil.createNewFile(file);
        }

        File target = testFile("bar.txt").toFile();
        FileUtil.renameFileUsingCopy(file, target);
        assertTrue(target.exists(), "File not copied");
        assertFalse(file.exists(), "File not deleted");
    }

    @Test
    public void testCompactHttpPath() {
        String in = "http://foo.com/apps/func/schemas/part/myap/dummy-schema.xsd";
        String out = FileUtil.compactPath(in, "/");
        assertEquals(in, out);
    }

    @Test
    public void testCompactHttpsPath() {
        String in = "https://foo.com/apps/func/schemas/part/myap/dummy-schema.xsd";
        String out = FileUtil.compactPath(in, "/");
        assertEquals(in, out);
    }

    @Test
    public void testCompactFilePath() {
        // should preserve the file: scheme prefix
        if (FileUtil.isWindows()) {
            assertEquals("file:..\\foo", FileUtil.compactPath("file:..\\foo"));
            assertEquals("file:..\\..\\foo", FileUtil.compactPath("file:..\\..\\foo"));
            assertEquals("file:..\\..\\foo\\bar", FileUtil.compactPath("file:..\\..\\foo\\bar"));
            assertEquals("file:..\\..\\foo", FileUtil.compactPath("file:..\\..\\foo\\bar\\.."));
            assertEquals("file:foo", FileUtil.compactPath("file:foo"));
            assertEquals("file:bar", FileUtil.compactPath("file:foo\\..\\bar"));
            assertEquals("file:bar\\baz", FileUtil.compactPath("file:foo\\..\\bar\\baz"));
            assertEquals("file:foo\\baz", FileUtil.compactPath("file:foo\\bar\\..\\baz"));
            assertEquals("file:baz", FileUtil.compactPath("file:foo\\bar\\..\\..\\baz"));
            assertEquals("file:..\\baz", FileUtil.compactPath("file:foo\\bar\\..\\..\\..\\baz"));
            assertEquals("file:..\\foo\\bar", FileUtil.compactPath("file:..\\foo\\bar"));
            assertEquals("file:foo\\bar\\baz", FileUtil.compactPath("file:foo\\bar\\.\\baz"));
            assertEquals("file:foo\\bar\\baz", FileUtil.compactPath("file:foo\\bar\\\\baz"));
            assertEquals("file:\\foo\\bar\\baz", FileUtil.compactPath("file:\\foo\\bar\\baz"));
        } else {
            assertEquals("file:../foo", FileUtil.compactPath("file:../foo"));
            assertEquals("file:../../foo", FileUtil.compactPath("file:../../foo"));
            assertEquals("file:../../foo/bar", FileUtil.compactPath("file:../../foo/bar"));
            assertEquals("file:../../foo", FileUtil.compactPath("file:../../foo/bar/.."));
            assertEquals("file:foo", FileUtil.compactPath("file:foo"));
            assertEquals("file:bar", FileUtil.compactPath("file:foo/../bar"));
            assertEquals("file:bar/baz", FileUtil.compactPath("file:foo/../bar/baz"));
            assertEquals("file:foo/baz", FileUtil.compactPath("file:foo/bar/../baz"));
            assertEquals("file:baz", FileUtil.compactPath("file:foo/bar/../../baz"));
            assertEquals("file:../baz", FileUtil.compactPath("file:foo/bar/../../../baz"));
            assertEquals("file:../foo/bar", FileUtil.compactPath("file:../foo/bar"));
            assertEquals("file:foo/bar/baz", FileUtil.compactPath("file:foo/bar/./baz"));
            assertEquals("file:foo/bar/baz", FileUtil.compactPath("file:foo/bar//baz"));
            assertEquals("file:/foo/bar/baz", FileUtil.compactPath("file:/foo/bar/baz"));
        }
    }

    @BeforeEach
    void createTestDir() throws IOException {
        Files.createDirectories(testDirectory());
    }

}
