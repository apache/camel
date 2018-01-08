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

import java.util.List;

import junit.framework.TestCase;

/**
 * Unit test for StringHelper
 */
public class StringHelperTest extends TestCase {

    public void testSimpleSanitized() {
        String out = StringHelper.sanitize("hello");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

    public void testNotFileFriendlySimpleSanitized() {
        String out = StringHelper.sanitize("c:\\helloworld");
        assertTrue("Should not contain : ", out.indexOf(':') == -1);
        assertTrue("Should not contain . ", out.indexOf('.') == -1);
    }

    public void testCountChar() {
        assertEquals(0, StringHelper.countChar("Hello World", 'x'));
        assertEquals(1, StringHelper.countChar("Hello World", 'e'));
        assertEquals(3, StringHelper.countChar("Hello World", 'l'));
        assertEquals(1, StringHelper.countChar("Hello World", ' '));
        assertEquals(0, StringHelper.countChar("", ' '));
        assertEquals(0, StringHelper.countChar(null, ' '));
    }

    public void testRemoveQuotes() throws Exception {
        assertEquals("Hello World", StringHelper.removeQuotes("Hello World"));
        assertEquals("", StringHelper.removeQuotes(""));
        assertEquals(null, StringHelper.removeQuotes(null));
        assertEquals(" ", StringHelper.removeQuotes(" "));
        assertEquals("foo", StringHelper.removeQuotes("'foo'"));
        assertEquals("foo", StringHelper.removeQuotes("'foo"));
        assertEquals("foo", StringHelper.removeQuotes("foo'"));
        assertEquals("foo", StringHelper.removeQuotes("\"foo\""));
        assertEquals("foo", StringHelper.removeQuotes("\"foo"));
        assertEquals("foo", StringHelper.removeQuotes("foo\""));
        assertEquals("foo", StringHelper.removeQuotes("'foo\""));
    }

    public void testRemoveLeadingAndEndingQuotes() throws Exception {
        assertEquals(null, StringHelper.removeLeadingAndEndingQuotes(null));
        assertEquals("", StringHelper.removeLeadingAndEndingQuotes(""));
        assertEquals(" ", StringHelper.removeLeadingAndEndingQuotes(" "));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("Hello World"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("'Hello World'"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("\"Hello World\""));
        assertEquals("Hello 'Camel'", StringHelper.removeLeadingAndEndingQuotes("Hello 'Camel'"));
    }

    public void testHasUpper() throws Exception {
        assertEquals(false, StringHelper.hasUpperCase(null));
        assertEquals(false, StringHelper.hasUpperCase(""));
        assertEquals(false, StringHelper.hasUpperCase(" "));
        assertEquals(false, StringHelper.hasUpperCase("com.foo"));
        assertEquals(false, StringHelper.hasUpperCase("com.foo.123"));

        assertEquals(true, StringHelper.hasUpperCase("com.foo.MyClass"));
        assertEquals(true, StringHelper.hasUpperCase("com.foo.My"));

        // Note, this is not a FQN
        assertEquals(true, StringHelper.hasUpperCase("com.foo.subA"));
    }

    public void testIsClassName() throws Exception {
        assertEquals(false, StringHelper.isClassName(null));
        assertEquals(false, StringHelper.isClassName(""));
        assertEquals(false, StringHelper.isClassName(" "));
        assertEquals(false, StringHelper.isClassName("com.foo"));
        assertEquals(false, StringHelper.isClassName("com.foo.123"));

        assertEquals(true, StringHelper.isClassName("com.foo.MyClass"));
        assertEquals(true, StringHelper.isClassName("com.foo.My"));

        // Note, this is not a FQN
        assertEquals(false, StringHelper.isClassName("com.foo.subA"));
    }

    public void testHasStartToken() throws Exception {
        assertEquals(false, StringHelper.hasStartToken(null, null));
        assertEquals(false, StringHelper.hasStartToken(null, "simple"));
        assertEquals(false, StringHelper.hasStartToken("", null));
        assertEquals(false, StringHelper.hasStartToken("", "simple"));
        assertEquals(false, StringHelper.hasStartToken("Hello World", null));
        assertEquals(false, StringHelper.hasStartToken("Hello World", "simple"));

        assertEquals(false, StringHelper.hasStartToken("${body}", null));
        assertEquals(true, StringHelper.hasStartToken("${body}", "simple"));
        assertEquals(true, StringHelper.hasStartToken("$simple{body}", "simple"));

        assertEquals(false, StringHelper.hasStartToken("${body}", null));
        assertEquals(false, StringHelper.hasStartToken("${body}", "foo"));
        // $foo{ is valid because its foo language
        assertEquals(true, StringHelper.hasStartToken("$foo{body}", "foo"));
    }

    public void testIsQuoted() throws Exception {
        assertEquals(false, StringHelper.isQuoted(null));
        assertEquals(false, StringHelper.isQuoted(""));
        assertEquals(false, StringHelper.isQuoted(" "));
        assertEquals(false, StringHelper.isQuoted("abc"));
        assertEquals(false, StringHelper.isQuoted("abc'"));
        assertEquals(false, StringHelper.isQuoted("\"abc'"));
        assertEquals(false, StringHelper.isQuoted("abc\""));
        assertEquals(false, StringHelper.isQuoted("'abc\""));
        assertEquals(false, StringHelper.isQuoted("\"abc'"));
        assertEquals(false, StringHelper.isQuoted("abc'def'"));
        assertEquals(false, StringHelper.isQuoted("abc'def'ghi"));
        assertEquals(false, StringHelper.isQuoted("'def'ghi"));

        assertEquals(true, StringHelper.isQuoted("'abc'"));
        assertEquals(true, StringHelper.isQuoted("\"abc\""));
    }

    public void testReplaceAll() throws Exception {
        assertEquals("", StringHelper.replaceAll("", "", ""));
        assertEquals(null, StringHelper.replaceAll(null, "", ""));
        assertEquals("foobar", StringHelper.replaceAll("foobar", "###", "DOT"));

        assertEquals("foobar", StringHelper.replaceAll("foo.bar", ".", ""));
        assertEquals("fooDOTbar", StringHelper.replaceAll("foo.bar", ".", "DOT"));
        assertEquals("fooDOTbar", StringHelper.replaceAll("foo###bar", "###", "DOT"));
        assertEquals("foobar", StringHelper.replaceAll("foo###bar", "###", ""));
        assertEquals("fooDOTbarDOTbaz", StringHelper.replaceAll("foo.bar.baz", ".", "DOT"));
        assertEquals("fooDOTbarDOTbazDOT", StringHelper.replaceAll("foo.bar.baz.", ".", "DOT"));
        assertEquals("DOTfooDOTbarDOTbazDOT", StringHelper.replaceAll(".foo.bar.baz.", ".", "DOT"));
        assertEquals("fooDOT", StringHelper.replaceAll("foo.", ".", "DOT"));
    }

    public void testRemoveInitialCharacters() throws Exception {
        assertEquals(StringHelper.removeStartingCharacters("foo", '/'), "foo");
        assertEquals(StringHelper.removeStartingCharacters("/foo", '/'), "foo");
        assertEquals(StringHelper.removeStartingCharacters("//foo", '/'), "foo");
    }

    public void testBefore() {
        assertEquals("Hello ", StringHelper.before("Hello World", "World"));
        assertEquals("Hello ", StringHelper.before("Hello World Again", "World"));
        assertEquals(null, StringHelper.before("Hello Again", "Foo"));

        assertTrue(StringHelper.before("mykey:ignore", ":", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.before("ignore:ignore", ":", "mykey"::equals).orElse(false));
    }

    public void testAfter() {
        assertEquals(" World", StringHelper.after("Hello World", "Hello"));
        assertEquals(" World Again", StringHelper.after("Hello World Again", "Hello"));
        assertEquals(null, StringHelper.after("Hello Again", "Foo"));

        assertTrue(StringHelper.after("ignore:mykey", ":", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.after("ignore:ignore", ":", "mykey"::equals).orElse(false));
    }

    public void testBetween() {
        assertEquals("foo bar", StringHelper.between("Hello 'foo bar' how are you", "'", "'"));
        assertEquals("foo bar", StringHelper.between("Hello ${foo bar} how are you", "${", "}"));
        assertEquals(null, StringHelper.between("Hello ${foo bar} how are you", "'", "'"));

        assertTrue(StringHelper.between("begin:mykey:end", "begin:", ":end", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.between("begin:ignore:end", "begin:", ":end", "mykey"::equals).orElse(false));
    }

    public void testBetweenOuterPair() {
        assertEquals("bar(baz)123", StringHelper.betweenOuterPair("foo(bar(baz)123)", '(', ')'));
        assertEquals(null, StringHelper.betweenOuterPair("foo(bar(baz)123))", '(', ')'));
        assertEquals(null, StringHelper.betweenOuterPair("foo(bar(baz123", '(', ')'));
        assertEquals(null, StringHelper.betweenOuterPair("foo)bar)baz123", '(', ')'));
        assertEquals("bar", StringHelper.betweenOuterPair("foo(bar)baz123", '(', ')'));
        assertEquals("'bar', 'baz()123', 123", StringHelper.betweenOuterPair("foo('bar', 'baz()123', 123)", '(', ')'));

        assertTrue(StringHelper.betweenOuterPair("foo(bar)baz123", '(', ')', "bar"::equals).orElse(false));
        assertFalse(StringHelper.betweenOuterPair("foo[bar)baz123", '(', ')', "bar"::equals).orElse(false));
    }

    public void testIsJavaIdentifier() {
        assertEquals(true, StringHelper.isJavaIdentifier("foo"));
        assertEquals(false, StringHelper.isJavaIdentifier("foo.bar"));
        assertEquals(false, StringHelper.isJavaIdentifier(""));
        assertEquals(false, StringHelper.isJavaIdentifier(null));
    }

    public void testNormalizeClassName() {
        assertEquals("Should get the right class name", "my.package-info", StringHelper.normalizeClassName("my.package-info"));
        assertEquals("Should get the right class name", "Integer[]", StringHelper.normalizeClassName("Integer[] \r"));
        assertEquals("Should get the right class name", "Hello_World", StringHelper.normalizeClassName("Hello_World"));
        assertEquals("Should get the right class name", "", StringHelper.normalizeClassName("////"));
    }

    public void testChangedLines() {
        String oldText = "Hello\nWorld\nHow are you";
        String newText = "Hello\nWorld\nHow are you";

        List<Integer> changed = StringHelper.changedLines(oldText, newText);
        assertEquals(0, changed.size());

        oldText = "Hello\nWorld\nHow are you";
        newText = "Hello\nWorld\nHow are you today";

        changed = StringHelper.changedLines(oldText, newText);
        assertEquals(1, changed.size());
        assertEquals(2, changed.get(0).intValue());

        oldText = "Hello\nWorld\nHow are you";
        newText = "Hello\nCamel\nHow are you today";

        changed = StringHelper.changedLines(oldText, newText);
        assertEquals(2, changed.size());
        assertEquals(1, changed.get(0).intValue());
        assertEquals(2, changed.get(1).intValue());

        oldText = "Hello\nWorld\nHow are you";
        newText = "Hello\nWorld\nHow are you today\nand tomorrow";

        changed = StringHelper.changedLines(oldText, newText);
        assertEquals(2, changed.size());
        assertEquals(2, changed.get(0).intValue());
        assertEquals(3, changed.get(1).intValue());
    }

    public void testTrimToNull() {
        assertEquals(StringHelper.trimToNull("abc"), "abc");
        assertEquals(StringHelper.trimToNull(" abc"), "abc");
        assertEquals(StringHelper.trimToNull(" abc "), "abc");
        assertNull(StringHelper.trimToNull(" "));
        assertNull(StringHelper.trimToNull("\t"));
        assertNull(StringHelper.trimToNull(" \t "));
        assertNull(StringHelper.trimToNull(""));
    }

    public void testHumanReadableBytes() {
        assertEquals("0 B",  StringHelper.humanReadableBytes(0));
        assertEquals("32 B",  StringHelper.humanReadableBytes(32));
        assertEquals("1.0 KB",  StringHelper.humanReadableBytes(1024));
        assertEquals("1.7 KB",  StringHelper.humanReadableBytes(1730));
        assertEquals("108.0 KB",  StringHelper.humanReadableBytes(110592));
        assertEquals("6.8 MB",  StringHelper.humanReadableBytes(7077888));
        assertEquals("432.0 MB",  StringHelper.humanReadableBytes(452984832));
        assertEquals("27.0 GB",  StringHelper.humanReadableBytes(28991029248L));
        assertEquals("1.7 TB",  StringHelper.humanReadableBytes(1855425871872L));
    }
}
