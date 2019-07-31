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
package org.apache.camel.component.exec.impl;

import java.util.List;

import org.junit.Test;

import static org.apache.camel.component.exec.impl.ExecParseUtils.isDoubleQuoted;
import static org.apache.camel.component.exec.impl.ExecParseUtils.isSingleQuoted;
import static org.apache.camel.component.exec.impl.ExecParseUtils.splitToWhiteSpaceSeparatedTokens;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ExecParseUtils}
 */
public class ExecParseUtilsTest {

    @Test
    public void testSingleQuoted() {
        assertTrue(isSingleQuoted("\"c:\\program files\\test\""));
    }

    @Test
    public void testSingleQuoted2() {
        assertTrue(isSingleQuoted("\"with space\""));
    }

    @Test
    public void testSingleQuotedNegative() {
        assertFalse(isSingleQuoted("arg 0"));
    }

    @Test
    public void testSingleQuotedNegative2() {
        assertFalse(isSingleQuoted("\" \" space not allowed between quotes \""));
    }

    @Test
    public void testSingleQuotedNegative3() {
        assertFalse(isSingleQuoted("\"\"double quoted is not single quoted\"\""));
    }

    @Test
    public void testEmptySingleQuotedNegative() {
        assertFalse(isSingleQuoted("\"\""));
    }

    @Test
    public void testEmptySingleQuotedNegative2() {
        assertFalse(isSingleQuoted("\""));
    }

    @Test
    public void testDoubleQuoted() {
        assertTrue(isDoubleQuoted("\"\"c:\\program files\\test\\\"\""));
    }

    @Test
    public void testEmptyDoubleQuotedNegative() {
        assertFalse(isDoubleQuoted("\"\"\"\""));
    }

    @Test
    public void testWhiteSpaceSeparatedArgs() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("arg0 arg1 arg2");
        assertEquals("arg0", args.get(0));
        assertEquals("arg1", args.get(1));
        assertEquals("arg2", args.get(2));
    }

    @Test
    public void testWhiteSpaceQuoted() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"arg 0\"");
        assertEquals("arg 0", args.get(0));
    }

    @Test
    public void testTwoQuotings() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"arg 0\" \"arg 1\"");
        assertEquals("arg 0", args.get(0));
        assertEquals("arg 1", args.get(1));
    }

    @Test
    public void testWhitespaceSeparatedArgsWithSpaces() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"arg 0 \"   arg1 \"arg 2\"");
        assertEquals("arg 0 ", args.get(0));
        assertEquals("arg1", args.get(1));
        assertEquals("arg 2", args.get(2));
    }

    @Test
    public void testDoubleQuote() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"\"arg0\"\"");
        assertEquals("\"arg0\"", args.get(0));
    }

    @Test
    public void testDoubleQuoteAndSpace() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"\"arg0\"\" arg1");
        assertEquals("\"arg0\"", args.get(0));
        assertEquals("arg1", args.get(1));
    }

    @Test
    public void testTwoDoubleQuotes() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("\"\"arg0\"\" \"\"arg1\"\"");
        assertEquals("\"arg0\"", args.get(0));
        assertEquals("\"arg1\"", args.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhiteSpaceSeparatedArgsNotClosed() {
        splitToWhiteSpaceSeparatedTokens("arg 0 \" arg1 \"arg 2\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidQuotes() {
        splitToWhiteSpaceSeparatedTokens("\"\"arg 0 \" arg1 \"arg 2\"");
    }
}
