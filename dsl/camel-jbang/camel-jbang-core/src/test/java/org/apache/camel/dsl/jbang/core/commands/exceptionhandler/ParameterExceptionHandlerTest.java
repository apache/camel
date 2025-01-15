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
package org.apache.camel.dsl.jbang.core.commands.exceptionhandler;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;

class ParameterExceptionHandlerTest {

    @Test
    @StdIo
    void testPluginMessage(StdErr err) {
        CamelJBangMain camelJBangMainNotExiting = createCamelJBangMain();
        CamelJBangMain.run(camelJBangMainNotExiting, "firstInvalid");

        String[] lines = err.capturedLines();
        Assertions.assertEquals(5, lines.length, "5 lines for the error is expected but received " + lines.length);
        Assertions.assertEquals("Unmatched argument at index 0: 'firstInvalid'", lines[0],
                "First line mentioning unmatched argument");
        Assertions.assertEquals("Did you mean: camel bind or camel infra or camel plugin?", lines[1],
                "Second line with suggestion in case it is a typo");
        Assertions.assertEquals(
                "Maybe a specific Camel JBang plugin must be installed? (Try camel plugin --help' for more information)",
                lines[4], "Last line suggesting new plugin");
    }

    @Test
    @StdIo
    void testNotPluginMessageWhenErrorNotOnFirstArgument(StdErr err) {
        CamelJBangMain camelJBangMainNotExiting = createCamelJBangMain();
        CamelJBangMain.run(camelJBangMainNotExiting, "plugin", "secondInvalid");

        String[] lines = err.capturedLines();
        Assertions.assertEquals(3, lines.length, "3 lines for the error is expected but received " + lines.length);
        Assertions.assertEquals("Unmatched argument at index 1: 'secondInvalid'", lines[0],
                "First line mentioning unmatched argument");
        Assertions.assertEquals("Usage: camel plugin [-h] [COMMAND]", lines[1], "Second line with usage");
        Assertions.assertEquals("Try 'camel plugin --help' for more information.", lines[2],
                "Last line with what to try to get help");
    }

    private CamelJBangMain createCamelJBangMain() {
        CamelJBangMain camelJBangMainNotExiting = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
                // Do not exit in unit test
            }
        };
        return camelJBangMainNotExiting;
    }

}
