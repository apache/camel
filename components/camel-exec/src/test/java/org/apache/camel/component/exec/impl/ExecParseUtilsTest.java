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

import static org.apache.camel.component.exec.impl.ExecParseUtils.splitToWhiteSpaceSeparatedTokens;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExecParseUtils}
 */
public class ExecParseUtilsTest {

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
        assertEquals("arg 0", args.get(0));
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
        List<String> args = splitToWhiteSpaceSeparatedTokens("'\"arg0\"' arg1");
        assertEquals("\"arg0\"", args.get(0));
        assertEquals("arg1", args.get(1));
    }

    @Test
    public void testTwoDoubleQuotes() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("'\"arg0\"' '\"arg1\"'");
        assertEquals("\"arg0\"", args.get(0));
        assertEquals("\"arg1\"", args.get(1));
    }

    @Test
    public void testRuby() {
        List<String> args = splitToWhiteSpaceSeparatedTokens("ruby -e 'puts \"Hello, world!\"'");
        assertEquals(3, args.size());
        assertEquals("ruby", args.get(0));
        assertEquals("-e", args.get(1));
        assertEquals("puts \"Hello, world!\"", args.get(2));
    }
}
