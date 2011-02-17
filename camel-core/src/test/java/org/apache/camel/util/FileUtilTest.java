/**
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

import junit.framework.TestCase;

/**
 * @version 
 */
public class FileUtilTest extends TestCase {

    public void testNormalizePath() {
        if (FileUtil.isWindows()) {
            assertEquals("foo\\bar", FileUtil.normalizePath("foo/bar"));
            assertEquals("foo\\bar\\baz", FileUtil.normalizePath("foo/bar\\baz"));
        } else {
            assertEquals("foo/bar", FileUtil.normalizePath("foo/bar"));
            assertEquals("foo/bar/baz", FileUtil.normalizePath("foo/bar\\baz"));
        }
    }

    public void testStripLeadingSeparator() {
        assertEquals(null, FileUtil.stripLeadingSeparator(null));
        assertEquals("foo", FileUtil.stripLeadingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("foo/bar"));
        assertEquals("foo/", FileUtil.stripLeadingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("/foo/bar"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("//foo/bar"));
        assertEquals("foo/bar", FileUtil.stripLeadingSeparator("///foo/bar"));
    }

    public void testHasLeadingSeparator() {
        assertEquals(false, FileUtil.hasLeadingSeparator(null));
        assertEquals(false, FileUtil.hasLeadingSeparator("foo"));
        assertEquals(false, FileUtil.hasLeadingSeparator("foo/bar"));
        assertEquals(false, FileUtil.hasLeadingSeparator("foo/"));
        assertEquals(true, FileUtil.hasLeadingSeparator("/foo/bar"));
        assertEquals(true, FileUtil.hasLeadingSeparator("//foo/bar"));
        assertEquals(true, FileUtil.hasLeadingSeparator("///foo/bar"));
    }

    public void testStripFirstLeadingSeparator() {
        assertEquals(null, FileUtil.stripFirstLeadingSeparator(null));
        assertEquals("foo", FileUtil.stripFirstLeadingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripFirstLeadingSeparator("foo/bar"));
        assertEquals("foo/", FileUtil.stripFirstLeadingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripFirstLeadingSeparator("/foo/bar"));
        assertEquals("/foo/bar", FileUtil.stripFirstLeadingSeparator("//foo/bar"));
        assertEquals("//foo/bar", FileUtil.stripFirstLeadingSeparator("///foo/bar"));
    }

    public void testStripTrailingSeparator() {
        assertEquals(null, FileUtil.stripTrailingSeparator(null));
        assertEquals("foo", FileUtil.stripTrailingSeparator("foo"));
        assertEquals("foo/bar", FileUtil.stripTrailingSeparator("foo/bar"));
        assertEquals("foo", FileUtil.stripTrailingSeparator("foo/"));
        assertEquals("foo/bar", FileUtil.stripTrailingSeparator("foo/bar/"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar/"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar//"));
        assertEquals("/foo/bar", FileUtil.stripTrailingSeparator("/foo/bar///"));
    }

    public void testStripPath() {
        assertEquals(null, FileUtil.stripPath(null));
        assertEquals("", FileUtil.stripPath("/"));
        assertEquals("foo.xml", FileUtil.stripPath("/foo.xml"));
        assertEquals("foo", FileUtil.stripPath("foo"));
        assertEquals("bar", FileUtil.stripPath("foo/bar"));
        assertEquals("bar", FileUtil.stripPath("/foo/bar"));
    }

    public void testStripPathWithMixedSeparators() {
        assertEquals(null, FileUtil.stripPath(null));
        assertEquals("", FileUtil.stripPath("/"));
        assertEquals("foo.xml", FileUtil.stripPath("/foo.xml"));
        assertEquals("foo", FileUtil.stripPath("foo"));
        assertEquals("baz", FileUtil.stripPath("foo/bar\\baz"));
        assertEquals("bar", FileUtil.stripPath("\\foo\\bar"));
        assertEquals("baz", FileUtil.stripPath("/foo\\bar/baz"));
    }

    public void testStripExt() {
        assertEquals(null, FileUtil.stripExt(null));
        assertEquals("foo", FileUtil.stripExt("foo"));
        assertEquals("foo", FileUtil.stripExt("foo.xml"));
        assertEquals("/foo/bar", FileUtil.stripExt("/foo/bar.xml"));
    }

    public void testOnlyPath() {
        assertEquals(null, FileUtil.onlyPath(null));
        assertEquals(null, FileUtil.onlyPath("foo"));
        assertEquals(null, FileUtil.onlyPath("foo.xml"));
        assertEquals("foo", FileUtil.onlyPath("foo/bar.xml"));
        assertEquals("/foo", FileUtil.onlyPath("/foo/bar.xml"));
        assertEquals("/foo/bar", FileUtil.onlyPath("/foo/bar/baz.xml"));
        assertEquals("/", FileUtil.onlyPath("/foo.xml"));
        assertEquals("/bar", FileUtil.onlyPath("/bar/foo.xml"));
    }

    public void testOnlyPathWithMixedSeparators() {
        assertEquals(null, FileUtil.onlyPath(null));
        assertEquals(null, FileUtil.onlyPath("foo"));
        assertEquals(null, FileUtil.onlyPath("foo.xml"));
        assertEquals("foo", FileUtil.onlyPath("foo/bar.xml"));
        assertEquals("/foo", FileUtil.onlyPath("/foo\\bar.xml"));
        assertEquals("\\foo\\bar", FileUtil.onlyPath("\\foo\\bar/baz.xml"));
        assertEquals("\\", FileUtil.onlyPath("\\foo.xml"));
        assertEquals("/bar", FileUtil.onlyPath("/bar\\foo.xml"));
    }

    public void testCompactPath() {
        assertEquals(null, FileUtil.compactPath(null));
        if (FileUtil.isWindows()) {
            assertEquals("foo", FileUtil.compactPath("foo"));
            assertEquals("bar", FileUtil.compactPath("foo\\..\\bar"));
            assertEquals("bar\\baz", FileUtil.compactPath("foo\\..\\bar\\baz"));
            assertEquals("foo\\baz", FileUtil.compactPath("foo\\bar\\..\\baz"));
            assertEquals("baz", FileUtil.compactPath("foo\\bar\\..\\..\\baz"));
            assertEquals("..\\baz", FileUtil.compactPath("foo\\bar\\..\\..\\..\\baz"));
            assertEquals("..\\foo\\bar", FileUtil.compactPath("..\\foo\\bar"));
        } else {
            assertEquals("foo", FileUtil.compactPath("foo"));
            assertEquals("bar", FileUtil.compactPath("foo/../bar"));
            assertEquals("bar/baz", FileUtil.compactPath("foo/../bar/baz"));
            assertEquals("foo/baz", FileUtil.compactPath("foo/bar/../baz"));
            assertEquals("baz", FileUtil.compactPath("foo/bar/../../baz"));
            assertEquals("../baz", FileUtil.compactPath("foo/bar/../../../baz"));
            assertEquals("../foo/bar", FileUtil.compactPath("../foo/bar"));
        }
    }

    public void testDefaultTempFileSuffixAndPrefix() throws Exception {
        File tmp = FileUtil.createTempFile("tmp-", ".tmp");
        assertNotNull(tmp);
        assertTrue("Should be a file", tmp.isFile());
    }

    public void testDefaultTempFile() throws Exception {
        File tmp = FileUtil.createTempFile(null, null);
        assertNotNull(tmp);
        assertTrue("Should be a file", tmp.isFile());
    }

    public void testDefaultTempFileParent() throws Exception {
        File tmp = FileUtil.createTempFile(null, null, new File("target"));
        assertNotNull(tmp);
        assertTrue("Should be a file", tmp.isFile());
    }

}
