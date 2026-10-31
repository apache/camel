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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.awt.*;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "start", description = "Start the Kaoto visual route editor",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel kaoto start",
                 "  camel kaoto start --port=9090",
                 "  camel kaoto start --dir=src/main/resources" })
public class KaotoStart extends CamelCommand {

    static final String STATE_FILE = ".camel-kaoto-port";

    @CommandLine.Option(names = { "--port" },
                        description = "Port number for the Kaoto web server", defaultValue = "8080")
    int port = 8080;

    @CommandLine.Option(names = { "--dir" },
                        description = "Source directory for Camel route files", defaultValue = ".")
    String dir = ".";

    @CommandLine.Option(names = { "--open-url", "--openUrl" }, arity = "0..1", fallbackValue = "true",
                        description = "Automatically open Kaoto in the web browser",
                        defaultValue = "true")
    boolean openUrl = true;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public KaotoStart(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (openUrl && !isOptionGiven("--open-url") && !EnvironmentHelper.isInteractiveTerminal()) {
            openUrl = false;
        }

        Path sourceDir = Path.of(dir).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            printer().printErr("Source directory does not exist: " + sourceDir);
            return 1;
        }

        Path stateFile = Path.of(STATE_FILE).toAbsolutePath();

        KaotoWebServer server = new KaotoWebServer(port, sourceDir, shutdownLatch);
        try {
            server.start();
        } catch (BindException e) {
            printer().printErr("Port " + port + " is already in use. Use --port to specify a different port.");
            return 1;
        } catch (IOException e) {
            printer().printErr("Failed to start Kaoto server: " + e.getMessage());
            return 1;
        }

        Files.writeString(stateFile, String.valueOf(port));

        String kaotoUrl = "http://localhost:" + port;
        printer().println("Kaoto visual route editor running at: " + kaotoUrl);
        printer().println("Serving routes from: " + sourceDir);
        printer().println("Press Ctrl+C to stop");

        if (openUrl && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(kaotoUrl));
            } catch (Exception e) {
                printer().printErr("Failed to open browser, access Kaoto at: " + kaotoUrl);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(shutdownLatch::countDown));

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop();
            Files.deleteIfExists(stateFile);
            printer().println("Kaoto stopped");
        }

        return 0;
    }
}
