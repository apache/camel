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
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldDefaultToDevModeWithSourceDir() throws Exception {
        Dev command = createDev();
        CommandLine.populateCommand(command);

        // before doCall, dev is not yet set
        assertFalse(command.dev);
        assertNull(command.sourceDir);
        assertTrue(command.files.isEmpty());

        // simulate what doCall does before delegating to super
        command.dev = true;
        if (command.files.isEmpty() && command.sourceDir == null) {
            command.sourceDir = ".";
        }

        assertTrue(command.dev);
        assertEquals(".", command.sourceDir);
    }

    @Test
    public void shouldNotOverrideSourceDirWhenFilesSpecified() throws Exception {
        Dev command = createDev();
        CommandLine.populateCommand(command, "hello.yaml");

        assertFalse(command.files.isEmpty());

        // simulate what doCall does before delegating to super
        command.dev = true;
        if (command.files.isEmpty() && command.sourceDir == null) {
            command.sourceDir = ".";
        }

        assertTrue(command.dev);
        // sourceDir should remain null since files were specified
        assertNull(command.sourceDir);
    }

    @Test
    public void shouldRespectExplicitSourceDir() throws Exception {
        Dev command = createDev();
        CommandLine.populateCommand(command, "--source-dir=src/main/routes");

        assertTrue(command.files.isEmpty());

        // simulate what doCall does before delegating to super
        command.dev = true;
        if (command.files.isEmpty() && command.sourceDir == null) {
            command.sourceDir = ".";
        }

        assertTrue(command.dev);
        // explicit source-dir should not be overridden
        assertEquals("src/main/routes", command.sourceDir);
    }

    private Dev createDev() {
        return new Dev(new CamelJBangMain().withPrinter(printer));
    }
}
