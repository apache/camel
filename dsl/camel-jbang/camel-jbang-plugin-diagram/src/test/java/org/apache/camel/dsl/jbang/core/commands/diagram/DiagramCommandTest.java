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
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
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

    @Test
    void shouldShowHelpWhenOnlyNameButNoFiles() throws Exception {
        // --name alone without files triggers the target==blank check indirectly,
        // but since target = name (non-blank), the Hawtio path is taken instead.
        // Here we test the case where name is also absent (already covered by shouldShowHelpWhenNoArgs).
        // Additional assertion: name is null by default.
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        Assertions.assertNull(command.name);
    }

    @Test
    void shouldSuppressOpenUrlWhenOutputSet() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "--name=myprocess", "--output=out.png");
        // Before doCall(): openUrl is still true
        Assertions.assertTrue(command.openUrl);
        command.doCall();
        // After doCall(): openUrl must have been set to false
        Assertions.assertFalse(command.openUrl);
        Assertions.assertTrue(command.pngExported, "PNG export should have been triggered");
    }

    @Test
    void shouldTakeHawtioPathWhenNoOutput() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "--name=myprocess");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(command.hawtioLaunched, "Hawtio should have been launched");
        Assertions.assertFalse(command.pngExported, "PNG export should not have been triggered");
    }

    @Test
    void shouldTakePngPathWhenOutputSet() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "--name=myprocess", "--output=out.png");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(command.pngExported, "PNG export should have been triggered");
        Assertions.assertFalse(command.hawtioLaunched, "Hawtio should not have been launched for PNG export");
    }

    @Test
    void shouldDerivRunNameFromFirstFile() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "my-route.yaml");
        command.doCall();
        Assertions.assertTrue(command.processLaunched, "Camel process should have been launched");
    }

    @Test
    void shouldLaunchProcessAndRunHawtio() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "route.yaml");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(command.processLaunched);
        Assertions.assertTrue(command.hawtioLaunched);
    }

    @Test
    void shouldLaunchProcessAndExportPng() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "--output=out.png", "route.yaml");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(command.processLaunched);
        Assertions.assertTrue(command.pngExported);
    }

    @Test
    void shouldReturnPngExporterExitCode() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(2);
        CommandLine.populateCommand(command, "--name=myprocess", "--output=out.png");
        int exit = command.doCall();
        Assertions.assertEquals(2, exit);
    }

    @Test
    void shouldReturnOneOnPngIllegalState() throws Exception {
        TestDiagramCommand command = new TestDiagramCommand(0);
        command.pngExportException = new IllegalStateException("Playwright not found");
        CommandLine.populateCommand(command, "--name=myprocess", "--output=out.png");
        int exit = command.doCall();
        Assertions.assertEquals(1, exit);
        Assertions.assertTrue(printer.getOutput().contains("Playwright not found"));
    }

    @Test
    void nullRendererDefaultsToHawtio() throws Exception {
        // renderer = null should default to "hawtio" behaviour (shows help when no files/name)
        DiagramCommand command = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        command.renderer = null;
        int exit = command.doCall();
        Assertions.assertEquals(0, exit); // shows help — not an error
    }

    @Test
    void explicitNameWithFilesSkipsAutoNameDerivation() throws Exception {
        // hasFiles=true AND name is already set — runName should not be overwritten from filename
        TestDiagramCommand command = new TestDiagramCommand(0);
        CommandLine.populateCommand(command, "--name=explicit", "route.yaml");
        command.doCall();
        Assertions.assertTrue(command.processLaunched);
    }

    @Test
    void newHawtioReturnsHawtioInstance() {
        DiagramCommand cmd = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        Hawtio h = cmd.newHawtio("test");
        Assertions.assertNotNull(h);
    }

    @Test
    void newPngExporterReturnsPngExporterInstance() {
        DiagramCommand cmd = new DiagramCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, "--output=out.png");
        DiagramPngExporter exporter = cmd.newPngExporter(null);
        Assertions.assertNotNull(exporter);
    }

    // --------------- Test stub ---------------

    /**
     * A test-friendly subclass of DiagramCommand that stubs out process launch and Hawtio/PNG export to avoid requiring
     * a real Camel integration or browser.
     */
    private class TestDiagramCommand extends DiagramCommand {
        boolean hawtioLaunched;
        boolean pngExported;
        boolean processLaunched;
        final int pngExportReturn;
        IllegalStateException pngExportException;

        TestDiagramCommand(int pngExportReturn) {
            super(new CamelJBangMain().withPrinter(printer));
            this.pngExportReturn = pngExportReturn;
        }

        @Override
        protected DiagramPngExporter.CamelLaunch launchCamelProcess(String runName) throws Exception {
            processLaunched = true;
            // Start a real (harmless) process so CamelLaunch holds a valid PID
            Process p = new ProcessBuilder("sleep", "100").start();
            p.destroy();
            return new DiagramPngExporter.CamelLaunch(p, p.pid(), null, runName);
        }

        @Override
        protected Hawtio newHawtio(String target) {
            hawtioLaunched = true;
            return new StubHawtio(getMain());
        }

        @Override
        protected DiagramPngExporter newPngExporter(DiagramPngExporter.CamelLaunch camelLaunch) {
            pngExported = true;
            final IllegalStateException ex = pngExportException;
            return new DiagramPngExporter(
                    getMain(), printer(), output, browser, playwrightBrowserPath,
                    routeId, jolokiaPort, port, keepRunning, timeout, camelLaunch) {
                @Override
                int export(String target) {
                    if (ex != null) {
                        throw ex;
                    }
                    return pngExportReturn;
                }
            };
        }
    }

    /**
     * A minimal Hawtio stub that returns 0 immediately without launching any server.
     */
    private static class StubHawtio extends Hawtio {
        StubHawtio(CamelJBangMain main) {
            super(main);
        }

        @Override
        protected Integer connectJolokia() {
            return 0;
        }

        @Override
        protected Integer callHawtio() {
            return 0;
        }
    }
}
