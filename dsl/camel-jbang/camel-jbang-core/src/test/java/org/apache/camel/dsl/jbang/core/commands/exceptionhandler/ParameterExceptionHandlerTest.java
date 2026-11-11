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

        // aesh: the error with the commands there are (or "did you mean" for a close one), the usage, then the hints
        String[] lines = err.capturedLines();
        Assertions.assertTrue(lines.length >= 4, "At least 4 lines for the error is expected but received " + lines.length);
        Assertions.assertTrue(lines[0].startsWith("ERROR: 'camel firstInvalid' is not a subcommand of 'camel'."),
                "First line mentioning the unknown command");
        Assertions.assertTrue(lines[1].startsWith("Usage: camel "), "Second line with the usage");
        Assertions.assertEquals("Try 'camel --help' for more information.", lines[lines.length - 2],
                "Second to last line with the help");
        Assertions.assertEquals(
                "Maybe a specific Camel CLI plugin must be installed? (Try 'camel plugin --help' for more information)",
                lines[lines.length - 1], "Last line suggesting new plugin");
    }

    @Test
    @StdIo
    void testNotPluginMessageWhenErrorNotOnFirstArgument(StdErr err) {
        CamelJBangMain camelJBangMainNotExiting = createCamelJBangMain();
        CamelJBangMain.run(camelJBangMainNotExiting, "plugin", "secondInvalid");

        String[] lines = err.capturedLines();
        Assertions.assertTrue(lines.length >= 3, "At least 3 lines for the error is expected but received " + lines.length);
        Assertions.assertTrue(lines[0].startsWith("ERROR: 'plugin secondInvalid' is not a subcommand of 'plugin'."),
                "First line mentioning the unknown sub-command");
        Assertions.assertTrue(lines[1].startsWith("Usage: camel plugin "), "Second line with the usage");
        Assertions.assertEquals("Try 'camel plugin --help' for more information.", lines[lines.length - 1],
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
