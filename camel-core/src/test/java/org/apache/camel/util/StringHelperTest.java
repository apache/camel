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

}
