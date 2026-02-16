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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DiagramTest extends CamelCommandBaseTestSupport {

    @Test
    void shouldRejectUnknownRenderer() throws Exception {
        Diagram command = new Diagram(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "--renderer=unknown");
        int exit = command.doCall();
        Assertions.assertEquals(1, exit);
        Assertions.assertTrue(printer.getOutput().contains("Unsupported renderer"));
    }

    @Test
    void shouldCollectFilesFromParameters() {
        Diagram command = new Diagram(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "a.yaml", "b.yaml");
        Assertions.assertEquals(List.of("a.yaml", "b.yaml"), command.files);
    }

    @Test
    void shouldShowHelpWhenNoArgs() throws Exception {
        Diagram command = new Diagram(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(printer.getOutput().isEmpty());
    }
}
