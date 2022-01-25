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

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for StringHelper
 */
public class StringHelperTest {

    @Test
    public void testSimpleSanitized() {
        String out = StringHelper.sanitize("hello");
        assertEquals(-1, out.indexOf(':'), "Should not contain : ");
        assertEquals(-1, out.indexOf('.'), "Should not contain . ");
    }

    @Test
    public void testNotFileFriendlySimpleSanitized() {
        String out = StringHelper.sanitize("c:\\helloworld");
        assertEquals(-1, out.indexOf(':'), "Should not contain : ");
        assertEquals(-1, out.indexOf('.'), "Should not contain . ");
    }

    @Test
    public void testSimpleCRLF() {
        String out = StringHelper.removeCRLF("hello");
        assertEquals("hello", out);
        boolean b6 = !out.contains("\r");
        assertTrue(b6, "Should not contain : ");
        boolean b5 = !out.contains("\n");
        assertTrue(b5, "Should not contain : ");

        out = StringHelper.removeCRLF("hello\r\n");
        assertEquals("hello", out);
        boolean b4 = !out.contains("\r");
        assertTrue(b4, "Should not contain : ");
        boolean b3 = !out.contains("\n");
        assertTrue(b3, "Should not contain : ");

        out = StringHelper.removeCRLF("\r\nhe\r\nllo\n");
        assertEquals("hello", out);
        boolean b2 = !out.contains("\r");
        assertTrue(b2, "Should not contain : ");
        boolean b1 = !out.contains("\n");
        assertTrue(b1, "Should not contain : ");

        out = StringHelper.removeCRLF("hello" + System.lineSeparator());
        assertEquals("hello", out);
        boolean b = !out.contains(System.lineSeparator());
        assertTrue(b, "Should not contain : ");
    }

    @Test
    public void testCountChar() {
        assertEquals(0, StringHelper.countChar("Hello World", 'x'));
        assertEquals(1, StringHelper.countChar("Hello World", 'e'));
        assertEquals(3, StringHelper.countChar("Hello World", 'l'));
        assertEquals(1, StringHelper.countChar("Hello World", ' '));
        assertEquals(0, StringHelper.countChar("", ' '));
        assertEquals(0, StringHelper.countChar(null, ' '));
    }

    @Test
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

    @Test
    public void testRemoveLeadingAndEndingQuotes() throws Exception {
        assertEquals(null, StringHelper.removeLeadingAndEndingQuotes(null));
        assertEquals("", StringHelper.removeLeadingAndEndingQuotes(""));
        assertEquals(" ", StringHelper.removeLeadingAndEndingQuotes(" "));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("Hello World"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("'Hello World'"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("\"Hello World\""));
        assertEquals("Hello 'Camel'", StringHelper.removeLeadingAndEndingQuotes("Hello 'Camel'"));
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testRemoveInitialCharacters() throws Exception {
        assertEquals("foo", StringHelper.removeStartingCharacters("foo", '/'));
        assertEquals("foo", StringHelper.removeStartingCharacters("/foo", '/'));
        assertEquals("foo", StringHelper.removeStartingCharacters("//foo", '/'));
    }

    @Test
    public void testBefore() {
        assertEquals("Hello ", StringHelper.before("Hello World", "World"));
        assertEquals("Hello ", StringHelper.before("Hello World Again", "World"));
        assertEquals(null, StringHelper.before("Hello Again", "Foo"));

        assertTrue(StringHelper.before("mykey:ignore", ":", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.before("ignore:ignore", ":", "mykey"::equals).orElse(false));

        assertEquals("", StringHelper.before("Hello World", "Test", ""));
        assertNull(StringHelper.before("Hello World", "Test", (String) null));

        assertEquals("a:b", StringHelper.beforeLast("a:b:c", ":"));
        assertEquals("", StringHelper.beforeLast("a:b:c", "_", ""));
    }

    @Test
    public void testAfter() {
        assertEquals(" World", StringHelper.after("Hello World", "Hello"));
        assertEquals(" World Again", StringHelper.after("Hello World Again", "Hello"));
        assertEquals(null, StringHelper.after("Hello Again", "Foo"));

        assertTrue(StringHelper.after("ignore:mykey", ":", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.after("ignore:ignore", ":", "mykey"::equals).orElse(false));

        assertEquals("", StringHelper.after("Hello World", "Test", ""));
        assertNull(StringHelper.after("Hello World", "Test", (String) null));

        assertEquals("c", StringHelper.afterLast("a:b:c", ":"));
        assertEquals("", StringHelper.afterLast("a:b:c", "_", ""));
    }

    @Test
    public void testBetween() {
        assertEquals("foo bar", StringHelper.between("Hello 'foo bar' how are you", "'", "'"));
        assertEquals("foo bar", StringHelper.between("Hello ${foo bar} how are you", "${", "}"));
        assertEquals(null, StringHelper.between("Hello ${foo bar} how are you", "'", "'"));

        assertTrue(StringHelper.between("begin:mykey:end", "begin:", ":end", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.between("begin:ignore:end", "begin:", ":end", "mykey"::equals).orElse(false));
    }

    @Test
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

    @Test
    public void testIsJavaIdentifier() {
        assertEquals(true, StringHelper.isJavaIdentifier("foo"));
        assertEquals(false, StringHelper.isJavaIdentifier("foo.bar"));
        assertEquals(false, StringHelper.isJavaIdentifier(""));
        assertEquals(false, StringHelper.isJavaIdentifier(null));
    }

    @Test
    public void testNormalizeClassName() {
        assertEquals("my.package-info", StringHelper.normalizeClassName("my.package-info"), "Should get the right class name");
        assertEquals("Integer[]", StringHelper.normalizeClassName("Integer[] \r"), "Should get the right class name");
        assertEquals("Hello_World", StringHelper.normalizeClassName("Hello_World"), "Should get the right class name");
        assertEquals("", StringHelper.normalizeClassName("////"), "Should get the right class name");
    }

    @Test
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

    @Test
    public void testTrimToNull() {
        assertEquals("abc", StringHelper.trimToNull("abc"));
        assertEquals("abc", StringHelper.trimToNull(" abc"));
        assertEquals("abc", StringHelper.trimToNull(" abc "));
        assertNull(StringHelper.trimToNull(" "));
        assertNull(StringHelper.trimToNull("\t"));
        assertNull(StringHelper.trimToNull(" \t "));
        assertNull(StringHelper.trimToNull(""));
    }

    @Test
    public void testHumanReadableBytes() {
        assertEquals("0 B", StringHelper.humanReadableBytes(Locale.ENGLISH, 0));
        assertEquals("32 B", StringHelper.humanReadableBytes(Locale.ENGLISH, 32));
        assertEquals("1.0 KB", StringHelper.humanReadableBytes(Locale.ENGLISH, 1024));
        assertEquals("1.7 KB", StringHelper.humanReadableBytes(Locale.ENGLISH, 1730));
        assertEquals("108.0 KB", StringHelper.humanReadableBytes(Locale.ENGLISH, 110592));
        assertEquals("6.8 MB", StringHelper.humanReadableBytes(Locale.ENGLISH, 7077888));
        assertEquals("432.0 MB", StringHelper.humanReadableBytes(Locale.ENGLISH, 452984832));
        assertEquals("27.0 GB", StringHelper.humanReadableBytes(Locale.ENGLISH, 28991029248L));
        assertEquals("1.7 TB", StringHelper.humanReadableBytes(Locale.ENGLISH, 1855425871872L));
    }

    @Test
    public void testHumanReadableBytesNullLocale() {
        assertEquals("1.3 KB", StringHelper.humanReadableBytes(null, 1280));
    }

    @Test
    public void testHumanReadableBytesDefaultLocale() {
        assertNotNull(StringHelper.humanReadableBytes(110592));
    }

    @Test
    public void testCapitalizeDash() {
        assertEquals(null, StringHelper.dashToCamelCase(null));
        assertEquals("", StringHelper.dashToCamelCase(""));
        assertEquals("hello", StringHelper.dashToCamelCase("hello"));
        assertEquals("helloGreat", StringHelper.dashToCamelCase("helloGreat"));
        assertEquals("helloGreat", StringHelper.dashToCamelCase("hello-great"));
        assertEquals("helloGreatWorld", StringHelper.dashToCamelCase("hello-great-world"));
    }

    public void testStartsWithIgnoreCase() {
        assertTrue(StringHelper.startsWithIgnoreCase(null, null));
        assertFalse(StringHelper.startsWithIgnoreCase("foo", null));
        assertFalse(StringHelper.startsWithIgnoreCase(null, "bar"));
        assertFalse(StringHelper.startsWithIgnoreCase("HelloWorld", "bar"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "Hello"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "hello"));
        assertFalse(StringHelper.startsWithIgnoreCase("HelloWorld", "Helo"));
        assertFalse(StringHelper.startsWithIgnoreCase("HelloWorld", "HelloWorld"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "helloWORLD"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "HELLO"));
        assertTrue(StringHelper.startsWithIgnoreCase("helloworld", "helloWORLD"));
        assertTrue(StringHelper.startsWithIgnoreCase("HELLOWORLD", "HELLO"));
    }

    @Test
    public void testSplitAsStream() {
        List<String> items = StringHelper.splitAsStream("a,b,c", ",").collect(Collectors.toList());
        assertTrue(items.contains("a"));
        assertTrue(items.contains("b"));
        assertTrue(items.contains("c"));
    }
}
