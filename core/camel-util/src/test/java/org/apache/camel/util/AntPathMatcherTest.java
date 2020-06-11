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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AntPathMatcher}.
 */
public class AntPathMatcherTest {

    @Test
    public void test() {
        AntPathMatcher matcher = new AntPathMatcher();
        assertTrue(matcher.match("*.txt", "blah.txt"));
        assertFalse(matcher.match("*.txt", "foo/blah.txt"));
        assertTrue(matcher.match("???.txt", "abc.txt"));
        assertTrue(matcher.match("abc.t?t", "abc.tnt"));
        assertFalse(matcher.match("???.txt", "abcd.txt"));
        assertTrue(matcher.match("**/*.txt", "blah.txt"));
        assertTrue(matcher.match("**/*.txt", "foo/blah.txt"));
        assertTrue(matcher.match("**/*.txt", "foo/bar/blah.txt"));
        assertTrue(matcher.match("foo/**/*.txt", "foo/bar/blah.txt"));
        assertTrue(matcher.match("foo/**/*.??", "foo/bar/blah.gz"));
        assertTrue(matcher.match("foo/**/*.txt", "foo/blah.txt"));
        assertFalse(matcher.match("foo/**/*.txt", "blah/blah.txt"));
    }

    @Test
    public void testCaseSensitive() {
        AntPathMatcher matcher = new AntPathMatcher();
        assertTrue(matcher.match("foo/**/*.txt", "foo/blah.txt", true));
        assertTrue(matcher.match("foo/**/*.txt", "foo/blah.txt", false));
        assertTrue(matcher.match("foo/**/*.txt", "foo/BLAH.txt"));
        assertFalse(matcher.match("FOO/**/*.txt", "foo/blah.txt"));
        assertFalse(matcher.match("foo/**/*.TXT", "foo/blah.txt"));
        assertTrue(matcher.match("foo/**/*.TXT", "foo/blah.txt", false));
        assertTrue(matcher.match("FOO/**/*.txt", "foo/blah.txt", false));
        assertFalse(matcher.match("FOO/**/*.txt", "foo/blah.txt", true));
        assertFalse(matcher.match("FOO/**/*.txt", "foo/blah.txt", true));
    }

    @Test
    public void testDetermineRoot() {
        AntPathMatcher matcher = new AntPathMatcher();
        assertEquals("org/apache/camel", matcher.determineRootDir("org/apache/camel"));
        assertEquals("org/apache/camel/", matcher.determineRootDir("org/apache/camel/"));
        assertEquals("org/apache/camel/", matcher.determineRootDir("org/apache/camel/*.xml"));
        assertEquals("WEB-INF/", matcher.determineRootDir("WEB-INF/*.xml"));

        // this is not a pattern
        assertEquals("org/apache/camel/mycamel.xml", matcher.determineRootDir("org/apache/camel/mycamel.xml"));
    }

}
