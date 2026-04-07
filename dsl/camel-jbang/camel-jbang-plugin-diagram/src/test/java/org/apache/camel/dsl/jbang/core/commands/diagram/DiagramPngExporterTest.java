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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramPngExporterTest {

    private StringPrinter printer;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
    }

    private DiagramPngExporter exporter(Path output, String browser) {
        return new DiagramPngExporter(
                new CamelJBangMain().withPrinter(printer),
                printer, output, browser,
                null, null, 8778, 8888,
                false, 10, null);
    }

    // --- export() early-exit paths ---

    @Test
    void exportShouldReturnOneWhenOutputIsNull() throws Exception {
        DiagramPngExporter exp = exporter(null, "chromium");
        assertEquals(1, exp.export("myRoute"));
        assertTrue(printer.getOutput().contains("Output file is required"));
    }

    @Test
    void exportShouldReturnOneForUnsupportedBrowser() throws Exception {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "firefox");
        assertEquals(1, exp.export("myRoute"));
        assertTrue(printer.getOutput().contains("Only chromium is supported"));
    }

    // --- static utility ---

    @Test
    void getJavaExecutableShouldReturnValidPath() {
        String exe = DiagramPngExporter.getJavaExecutable();
        assertNotNull(exe);
        assertFalse(exe.isBlank());
        assertTrue(exe.endsWith("java") || exe.endsWith("java.exe"),
                "Expected executable ending in 'java' but got: " + exe);
    }

    // --- resolveBrowserPath ---

    @Test
    void resolveBrowserPathReturnsExplicitPath() {
        DiagramPngExporter exp = new DiagramPngExporter(
                new CamelJBangMain().withPrinter(printer),
                printer, Path.of("out.png"), "chromium",
                "/usr/bin/chromium", null, 8778, 8888, false, 10, null);
        assertEquals("/usr/bin/chromium", exp.resolveBrowserPath());
    }

    @Test
    void resolveBrowserPathDoesNotThrowWhenNothingSet() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        assertDoesNotThrow(exp::resolveBrowserPath);
    }

    // --- buildClassPath ---

    @Test
    void buildClassPathJoinsJarsWithSeparator() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        String cp = exp.buildClassPath(
                List.of(Path.of("/tmp/a.jar"), Path.of("/tmp/b.jar")),
                Path.of("/tmp/plugin.jar"));
        assertTrue(cp.contains("a.jar"));
        assertTrue(cp.contains("b.jar"));
        assertTrue(cp.contains("plugin.jar"));
    }

    @Test
    void buildClassPathWithEmptyListJustIncludesPlugin() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        String cp = exp.buildClassPath(List.of(), Path.of("/tmp/plugin.jar"));
        assertTrue(cp.contains("plugin.jar"));
    }

    // --- isPlaywrightCached ---

    @Test
    void isPlaywrightCachedDoesNotThrow() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        assertDoesNotThrow(exp::isPlaywrightCached);
    }

    // --- checkJolokia ---

    @Test
    void checkJolokiaReturnsNullOnHttp200() throws IOException {
        HttpServer server = startServer(200);
        try {
            DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
            assertNull(exp.checkJolokia("http://127.0.0.1:" + server.getAddress().getPort() + "/jolokia"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkJolokiaReturnsNullOnHttp401() throws IOException {
        // 401 is acceptable — server is up, just needs auth
        HttpServer server = startServer(401);
        try {
            DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
            assertNull(exp.checkJolokia("http://127.0.0.1:" + server.getAddress().getPort() + "/jolokia"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkJolokiaReturnsNullWhenPortIsListening() throws IOException {
        // TCP socket check: any open port means service is up, regardless of HTTP response code
        HttpServer server = startServer(404);
        try {
            DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
            assertNull(exp.checkJolokia("http://127.0.0.1:" + server.getAddress().getPort() + "/jolokia"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void checkJolokiaReturnsErrorWhenConnectionRefused() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        // Use a port unlikely to be listening
        String result = exp.checkJolokia("http://127.0.0.1:19977/jolokia");
        assertNotNull(result, "Expected error string when connection refused");
    }

    // --- stopProcess ---

    @Test
    void stopProcessHandlesNullGracefully() {
        DiagramPngExporter exp = exporter(Path.of("out.png"), "chromium");
        assertDoesNotThrow(() -> exp.stopProcess(null));
    }

    @Test
    void stopProcessCallsDestroyOnProcess() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor(anyLong(), any())).thenReturn(true);
        exporter(Path.of("out.png"), "chromium").stopProcess(mockProcess);
        verify(mockProcess).destroy();
    }

    @Test
    void stopProcessCallsDestroyForciblyWhenWaitForTimesOut() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor(anyLong(), any())).thenReturn(false);
        exporter(Path.of("out.png"), "chromium").stopProcess(mockProcess);
        verify(mockProcess).destroyForcibly();
    }

    @Test
    void stopProcessReInterruptsOnInterruptedException() throws Exception {
        Process mockProcess = mock(Process.class);
        when(mockProcess.waitFor(anyLong(), any())).thenThrow(new InterruptedException("test"));
        Thread.currentThread().interrupted(); // clear
        exporter(Path.of("out.png"), "chromium").stopProcess(mockProcess);
        assertTrue(Thread.currentThread().isInterrupted(), "Thread should be re-interrupted");
        Thread.interrupted(); // clean up
    }

    // --- helpers ---

    private HttpServer startServer(int statusCode) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jolokia/version", exchange -> {
            exchange.sendResponseHeaders(statusCode, 0);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }
}
