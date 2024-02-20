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

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.StringHelper.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class StringHelperTest {

    @Test
    public void testCamelCashToDash() {
        assertNull(camelCaseToDash(null));
        assertEquals("", camelCaseToDash(""));

        assertEquals("hello-world", camelCaseToDash("HelloWorld"));
        assertEquals("hello-big-world", camelCaseToDash("HelloBigWorld"));
        assertEquals("hello-big-world", camelCaseToDash("Hello-bigWorld"));
        assertEquals("my-id", camelCaseToDash("MyId"));
        assertEquals("my-id", camelCaseToDash("MyID"));
        assertEquals("my-url", camelCaseToDash("MyUrl"));
        assertEquals("my-url", camelCaseToDash("MyURL"));
        assertEquals("my-big-id", camelCaseToDash("MyBigId"));
        assertEquals("my-big-id", camelCaseToDash("MyBigID"));
        assertEquals("my-big-url", camelCaseToDash("MyBigUrl"));
        assertEquals("my-big-url", camelCaseToDash("MyBigURL"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIdAgain"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIDAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigUrlAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigURLAgain"));

        assertEquals("use-mdc-logging", camelCaseToDash("UseMDCLogging"));
        assertEquals("mdc-logging-keys-pattern", camelCaseToDash("MDCLoggingKeysPattern"));
        assertEquals("available-phone-number-country", camelCaseToDash("AVAILABLE_PHONE_NUMBER_COUNTRY"));
        assertEquals("available-phone-number-country", camelCaseToDash("AVAILABLE-PHONE_NUMBER-COUNTRY"));
        assertEquals("available-phone-number-country", camelCaseToDash("Available-Phone-Number-Country"));
        assertEquals("available-phone-number-country", camelCaseToDash("Available_Phone_Number_Country"));
        assertEquals("available-phone-number-country", camelCaseToDash("available_phone_number_country"));
        assertEquals("available-phone-number-country", camelCaseToDash("availablePhoneNumberCountry"));
        assertEquals("available-phone-number-country", camelCaseToDash("AvailablePhoneNumberCountry"));
    }

    @Test
    public void testDashToCamelCaseSkipQuotedOrKeyed() {
        String line = "camel.component.rabbitmq.args[queue.x-queue-type]";
        // no preserve
        assertEquals("camel.component.rabbitmq.args[queue.xQueueType]",
                dashToCamelCase(line));

        // preserved
        assertEquals(line, dashToCamelCase(line, true));
    }

    @Nested
    class DashToCamelCase {

        @Test
        void testDashToCamelCaseWithNull() {
            assertThat(dashToCamelCase(null)).isNull();
        }

        @Test
        void testDashToCamelCaseWithEmptyValue() {
            assertThat(dashToCamelCase("")).isEmpty();
        }

        @Test
        void testDashToCamelCaseWithNoDash() {
            assertThat(dashToCamelCase("a")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithOneDash() {
            assertThat(dashToCamelCase("a-b")).isEqualTo("aB");
        }

        @Test
        void testDashToCamelCaseWithSeveralDashes() {
            assertThat(dashToCamelCase("a-bb-cc-dd")).isEqualTo("aBbCcDd");
        }

        @Test
        void testDashToCamelCaseWithEndDash() {
            assertThat(dashToCamelCase("a-")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithEndDashes() {
            assertThat(dashToCamelCase("a----")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithSeceralDashesGrouped() {
            assertThat(dashToCamelCase("a--b")).isEqualTo("aB");
        }
    }

    @Test
    public void testSplitWords() {
        String[] arr = splitWords("apiName/methodName");
        assertEquals(2, arr.length);
        assertEquals("apiName", arr[0]);
        assertEquals("methodName", arr[1]);

        arr = splitWords("hello");
        assertEquals(1, arr.length);
        assertEquals("hello", arr[0]);
    }

    @Test
    public void testReplaceFirst() {
        assertEquals("jms:queue:bar", replaceFirst("jms:queue:bar", "foo", "bar"));
        assertEquals("jms:queue:bar", replaceFirst("jms:queue:foo", "foo", "bar"));
        assertEquals("jms:queue:bar?blah=123", replaceFirst("jms:queue:foo?blah=123", "foo", "bar"));
        assertEquals("jms:queue:bar?blah=foo", replaceFirst("jms:queue:foo?blah=foo", "foo", "bar"));
    }

    @Test
    public void testRemoveLeadingAndEndingQuotes() {
        assertEquals("abc", removeLeadingAndEndingQuotes("'abc'"));
        assertEquals("abc", removeLeadingAndEndingQuotes("\"abc\""));
        assertEquals("a'b'c", removeLeadingAndEndingQuotes("a'b'c"));
        assertEquals("'b'c", removeLeadingAndEndingQuotes("'b'c"));
        assertEquals("", removeLeadingAndEndingQuotes("''"));
        assertEquals("'", removeLeadingAndEndingQuotes("'"));
    }

    @Test
    public void testRemoveLeadingAndEndingQuotesWithSpaces() {
        assertNull(StringHelper.removeLeadingAndEndingQuotes(null));
        assertEquals(" ", StringHelper.removeLeadingAndEndingQuotes(" "));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("Hello World"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("'Hello World'"));
        assertEquals("Hello World", StringHelper.removeLeadingAndEndingQuotes("\"Hello World\""));
        assertEquals("Hello 'Camel'", StringHelper.removeLeadingAndEndingQuotes("Hello 'Camel'"));
    }

    @Test
    public void testSplitOnCharacterAsList() {
        List<String> list = splitOnCharacterAsList("foo", ',', 1);
        assertEquals(1, list.size());
        assertEquals("foo", list.get(0));

        list = splitOnCharacterAsList("foo,bar", ',', 2);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));

        list = splitOnCharacterAsList("foo,bar,", ',', 3);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));

        list = splitOnCharacterAsList(",foo,bar", ',', 3);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));

        list = splitOnCharacterAsList(",foo,bar,", ',', 4);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i);
            sb.append(",");
        }
        String value = sb.toString();

        int count = StringHelper.countChar(value, ',') + 1;
        list = splitOnCharacterAsList(value, ',', count);
        assertEquals(100, list.size());
        assertEquals("0", list.get(0));
        assertEquals("50", list.get(50));
        assertEquals("99", list.get(99));
    }

    @Test
    public void testSplitOnCharacterAsIterator() {
        Iterator<String> it = splitOnCharacterAsIterator("foo", ',', 1);
        assertEquals("foo", it.next());
        assertFalse(it.hasNext());

        it = splitOnCharacterAsIterator("foo,bar", ',', 2);
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
        assertFalse(it.hasNext());

        it = splitOnCharacterAsIterator("foo,bar,", ',', 3);
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
        assertFalse(it.hasNext());

        it = splitOnCharacterAsIterator(",foo,bar", ',', 3);
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
        assertFalse(it.hasNext());

        it = splitOnCharacterAsIterator(",foo,bar,", ',', 4);
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
        assertFalse(it.hasNext());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i);
            sb.append(",");
        }
        String value = sb.toString();

        int count = StringHelper.countChar(value, ',') + 1;
        it = splitOnCharacterAsIterator(value, ',', count);
        for (int i = 0; i < 100; i++) {
            assertEquals(Integer.toString(i), it.next());
        }
        assertFalse(it.hasNext());
    }

    @Test
    public void testPad() {
        assertEquals("  ", StringHelper.padString(1));
        assertEquals("    ", StringHelper.padString(2));
        assertEquals("      ", StringHelper.padString(3));
        assertEquals("   ", StringHelper.padString(3, 1));
        assertEquals(" ", StringHelper.padString(1, 1));
        assertEquals("", StringHelper.padString(0));
        assertEquals("", StringHelper.padString(0, 2));
    }

    @Test
    public void testFillChars() {
        assertEquals("", StringHelper.fillChars('-', 0));
        assertEquals("==", StringHelper.fillChars('=', 2));
        assertEquals("----", StringHelper.fillChars('-', 4));
        assertEquals("..........", StringHelper.fillChars('.', 10));
    }

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
    public void testRemoveQuotes() {
        assertEquals("Hello World", StringHelper.removeQuotes("Hello World"));
        assertEquals("", StringHelper.removeQuotes(""));
        assertNull(StringHelper.removeQuotes(null));
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
    public void testHasUpper() {
        assertFalse(StringHelper.hasUpperCase(null));
        assertFalse(StringHelper.hasUpperCase(""));
        assertFalse(StringHelper.hasUpperCase(" "));
        assertFalse(StringHelper.hasUpperCase("com.foo"));
        assertFalse(StringHelper.hasUpperCase("com.foo.123"));

        assertTrue(StringHelper.hasUpperCase("com.foo.MyClass"));
        assertTrue(StringHelper.hasUpperCase("com.foo.My"));

        // Note, this is not a FQN
        assertTrue(StringHelper.hasUpperCase("com.foo.subA"));
    }

    @Test
    public void testIsClassName() {
        assertFalse(StringHelper.isClassName(null));
        assertFalse(StringHelper.isClassName(""));
        assertFalse(StringHelper.isClassName(" "));
        assertFalse(StringHelper.isClassName("com.foo"));
        assertFalse(StringHelper.isClassName("com.foo.123"));

        assertTrue(StringHelper.isClassName("com.foo.MyClass"));
        assertTrue(StringHelper.isClassName("com.foo.My"));

        // Note, this is not a FQN
        assertFalse(StringHelper.isClassName("com.foo.subA"));
    }

    @Test
    public void testHasStartToken() {
        assertFalse(StringHelper.hasStartToken(null, null));
        assertFalse(StringHelper.hasStartToken(null, "simple"));
        assertFalse(StringHelper.hasStartToken("", null));
        assertFalse(StringHelper.hasStartToken("", "simple"));
        assertFalse(StringHelper.hasStartToken("Hello World", null));
        assertFalse(StringHelper.hasStartToken("Hello World", "simple"));

        assertFalse(StringHelper.hasStartToken("${body}", null));
        assertTrue(StringHelper.hasStartToken("${body}", "simple"));
        assertTrue(StringHelper.hasStartToken("$simple{body}", "simple"));

        assertFalse(StringHelper.hasStartToken("${body}", null));
        assertFalse(StringHelper.hasStartToken("${body}", "foo"));
        // $foo{ is valid because its foo language
        assertTrue(StringHelper.hasStartToken("$foo{body}", "foo"));
    }

    @Test
    public void testIsQuoted() {
        assertFalse(StringHelper.isQuoted(null));
        assertFalse(StringHelper.isQuoted(""));
        assertFalse(StringHelper.isQuoted(" "));
        assertFalse(StringHelper.isQuoted("abc"));
        assertFalse(StringHelper.isQuoted("abc'"));
        assertFalse(StringHelper.isQuoted("\"abc'"));
        assertFalse(StringHelper.isQuoted("abc\""));
        assertFalse(StringHelper.isQuoted("'abc\""));
        assertFalse(StringHelper.isQuoted("\"abc'"));
        assertFalse(StringHelper.isQuoted("abc'def'"));
        assertFalse(StringHelper.isQuoted("abc'def'ghi"));
        assertFalse(StringHelper.isQuoted("'def'ghi"));

        assertTrue(StringHelper.isQuoted("'abc'"));
        assertTrue(StringHelper.isQuoted("\"abc\""));
    }

    @Test
    public void testRemoveInitialCharacters() {
        assertEquals("foo", StringHelper.removeStartingCharacters("foo", '/'));
        assertEquals("foo", StringHelper.removeStartingCharacters("/foo", '/'));
        assertEquals("foo", StringHelper.removeStartingCharacters("//foo", '/'));
    }

    @Test
    public void testBefore() {
        assertEquals("Hello ", StringHelper.before("Hello World", "World"));
        assertEquals("Hello ", StringHelper.before("Hello World Again", "World"));
        assertNull(StringHelper.before("Hello Again", "Foo"));

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
        assertNull(StringHelper.after("Hello Again", "Foo"));

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
        assertNull(StringHelper.between("Hello ${foo bar} how are you", "'", "'"));

        assertTrue(StringHelper.between("begin:mykey:end", "begin:", ":end", "mykey"::equals).orElse(false));
        assertFalse(StringHelper.between("begin:ignore:end", "begin:", ":end", "mykey"::equals).orElse(false));
    }

    @Test
    public void testBetweenOuterPair() {
        assertEquals("bar(baz)123", StringHelper.betweenOuterPair("foo(bar(baz)123)", '(', ')'));
        assertNull(StringHelper.betweenOuterPair("foo(bar(baz)123))", '(', ')'));
        assertNull(StringHelper.betweenOuterPair("foo(bar(baz123", '(', ')'));
        assertNull(StringHelper.betweenOuterPair("foo)bar)baz123", '(', ')'));
        assertEquals("bar", StringHelper.betweenOuterPair("foo(bar)baz123", '(', ')'));
        assertEquals("'bar', 'baz()123', 123", StringHelper.betweenOuterPair("foo('bar', 'baz()123', 123)", '(', ')'));

        assertTrue(StringHelper.betweenOuterPair("foo(bar)baz123", '(', ')', "bar"::equals).orElse(false));
        assertFalse(StringHelper.betweenOuterPair("foo[bar)baz123", '(', ')', "bar"::equals).orElse(false));
    }

    @Test
    public void testIsJavaIdentifier() {
        assertTrue(StringHelper.isJavaIdentifier("foo"));
        assertFalse(StringHelper.isJavaIdentifier("foo.bar"));
        assertFalse(StringHelper.isJavaIdentifier(""));
        assertFalse(StringHelper.isJavaIdentifier(null));
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
        assertNull(StringHelper.dashToCamelCase(null));
        assertEquals("", StringHelper.dashToCamelCase(""));
        assertEquals("hello", StringHelper.dashToCamelCase("hello"));
        assertEquals("helloGreat", StringHelper.dashToCamelCase("helloGreat"));
        assertEquals("helloGreat", StringHelper.dashToCamelCase("hello-great"));
        assertEquals("helloGreatWorld", StringHelper.dashToCamelCase("hello-great-world"));
    }

    @Test
    public void testStartsWithIgnoreCase() {
        assertTrue(StringHelper.startsWithIgnoreCase(null, null));
        assertFalse(StringHelper.startsWithIgnoreCase("foo", null));
        assertFalse(StringHelper.startsWithIgnoreCase(null, "bar"));
        assertFalse(StringHelper.startsWithIgnoreCase("HelloWorld", "bar"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "Hello"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "hello"));
        assertFalse(StringHelper.startsWithIgnoreCase("HelloWorld", "Helo"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "HelloWorld"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "helloWORLD"));
        assertTrue(StringHelper.startsWithIgnoreCase("HelloWorld", "HELLO"));
        assertTrue(StringHelper.startsWithIgnoreCase("helloworld", "helloWORLD"));
        assertTrue(StringHelper.startsWithIgnoreCase("HELLOWORLD", "HELLO"));
    }

    @Test
    public void testSplitAsStream() {
        List<String> items = StringHelper.splitAsStream("a,b,c", ",").toList();
        assertTrue(items.contains("a"));
        assertTrue(items.contains("b"));
        assertTrue(items.contains("c"));
    }

    @Test
    public void testSplitOnCharacter() {
        String[] list = splitOnCharacter("foo", "'", 1);
        assertEquals(1, list.length);
        assertEquals("foo", list[0]);

        list = splitOnCharacter("foo,bar", ",", 2);
        assertEquals(2, list.length);
        assertEquals("foo", list[0]);
        assertEquals("bar", list[1]);

        list = splitOnCharacter("foo,bar,", ",", 3);
        assertEquals(3, list.length);
        assertEquals("foo", list[0]);
        assertEquals("bar", list[1]);

        list = splitOnCharacter(",foo,bar", ",", 3);
        assertEquals(3, list.length);
        assertEquals("foo", list[1]);
        assertEquals("bar", list[2]);

        list = splitOnCharacter(",foo,bar,", ",", 4);
        assertEquals(4, list.length);
        assertEquals("foo", list[1]);
        assertEquals("bar", list[2]);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(i);
            sb.append(",");
        }
        String value = sb.toString();

        int count = StringHelper.countChar(value, ',') + 1;
        list = splitOnCharacter(value, ",", count);
        assertEquals(101, list.length);
        assertEquals("0", list[0]);
        assertEquals("50", list[50]);
        assertEquals("99", list[99]);
    }
}
