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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DiagramCommandTest {

    private StringPrinter printer;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
    }

    @Test
    void shouldRejectUnknownRenderer() throws Exception {
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "--renderer=unknown");
        int exit = command.doCall();
        Assertions.assertEquals(1, exit);
        Assertions.assertTrue(printer.getOutput().contains("Unsupported renderer"));
    }

    @Test
    void shouldCollectFilesFromParameters() {
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "a.yaml", "b.yaml");
        Assertions.assertEquals(List.of("a.yaml", "b.yaml"), command.files);
    }

    @Test
    void shouldShowHelpWhenNoArgs() throws Exception {
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        // No error output — showing help is a success case
    }

    @Test
    void openUrlDefaultsToTrue() {
        // Verify the default: openUrl is true until doCall() suppresses it when --output is set.
        // Testing the doCall() suppression requires an integration test with a mock exporter.
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "--output=diagram.png", "my-route.yaml");
        Assertions.assertTrue(command.openUrl, "openUrl should default to true at the binding stage");
    }

    @Test
    void shouldPopulateExportOptions() {
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                "--output=routes.png",
                "--browser=chromium",
                "--playwright-browser-path=/bin/chromium",
                "--route-id=route1",
                "--jolokia-port=8889");
        Assertions.assertEquals(Paths.get("routes.png"), command.output);
        Assertions.assertEquals("chromium", command.browser);
        Assertions.assertEquals("/bin/chromium", command.playwrightBrowserPath);
        Assertions.assertEquals("route1", command.routeId);
        Assertions.assertEquals(8889, command.jolokiaPort);
    }
}
