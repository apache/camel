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
package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AskCliToolsTest {

    @Test
    void tokenizeSimpleCommand() {
        assertArrayEquals(
                new String[] { "get", "routes" },
                Ask.tokenizeCommand("get routes"));
    }

    @Test
    void tokenizeCommandWithDoubleQuotes() {
        assertArrayEquals(
                new String[] { "get", "route", "--filter=my route" },
                Ask.tokenizeCommand("get route --filter=\"my route\""));
    }

    @Test
    void tokenizeCommandWithSingleQuotes() {
        assertArrayEquals(
                new String[] { "get", "route", "--filter=my route" },
                Ask.tokenizeCommand("get route --filter='my route'"));
    }

    @Test
    void tokenizeCommandWithExtraSpaces() {
        assertArrayEquals(
                new String[] { "catalog", "component", "--filter=kafka" },
                Ask.tokenizeCommand("  catalog   component   --filter=kafka  "));
    }

    @Test
    void tokenizeEmptyCommand() {
        assertEquals(0, Ask.tokenizeCommand("").length);
        assertEquals(0, Ask.tokenizeCommand("   ").length);
    }

    @Test
    void tokenizeSingleArg() {
        assertArrayEquals(
                new String[] { "ps" },
                Ask.tokenizeCommand("ps"));
    }
}
